package com.xiaoyiluck.meoweco.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class VaultAsyncOperationManager {
    public enum State {
        ACCEPTING,
        SHUTTING_DOWN,
        STOPPED
    }

    public record ShutdownResult(boolean drained, CompletableFuture<Void> termination) {
    }

    private final Object lock = new Object();
    private final Executor executor;
    private final Set<Operation<?>> operations = new HashSet<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private State state = State.ACCEPTING;

    public VaultAsyncOperationManager(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        Operation<T> operation = new Operation<>(supplier);
        RuntimeException schedulingFailure = null;

        synchronized (lock) {
            if (state != State.ACCEPTING) {
                return CompletableFuture.failedFuture(
                        new RejectedExecutionException("Vault v2 async operations are shutting down"));
            }
            operations.add(operation);
            try {
                executor.execute(operation);
            } catch (RuntimeException error) {
                operation.status = OperationStatus.FINISHED;
                operations.remove(operation);
                schedulingFailure = error;
            }
        }

        if (schedulingFailure != null) {
            operation.future.completeExceptionally(schedulingFailure);
        }
        return operation.future;
    }

    public ShutdownResult shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        List<CompletableFuture<?>> queuedFutures = new ArrayList<>();
        boolean stopped;
        synchronized (lock) {
            if (state == State.ACCEPTING) {
                state = State.SHUTTING_DOWN;
            }
            for (Operation<?> operation : List.copyOf(operations)) {
                if (operation.status == OperationStatus.QUEUED) {
                    operation.status = OperationStatus.CANCELLED;
                    operations.remove(operation);
                    queuedFutures.add(operation.future);
                }
            }
            stopped = stopIfDrainedLocked();
        }

        RejectedExecutionException rejection =
                new RejectedExecutionException("Vault v2 async operation cancelled during shutdown");
        queuedFutures.forEach(future -> future.completeExceptionally(rejection));
        if (stopped) {
            termination.complete(null);
        }

        boolean drained = awaitTermination(timeout);
        if (!drained) {
            failRunningFutures(new TimeoutException("Vault v2 async shutdown timed out"));
        }
        return new ShutdownResult(drained, termination);
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public int trackedOperationCount() {
        synchronized (lock) {
            return operations.size();
        }
    }

    private boolean awaitTermination(Duration timeout) {
        if (termination.isDone()) {
            return true;
        }
        try {
            termination.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException ignored) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failRunningFutures(new CancellationException("Vault v2 async shutdown was interrupted"));
            return false;
        } catch (ExecutionException impossible) {
            throw new IllegalStateException("Vault v2 async termination failed", impossible);
        }
    }

    private void failRunningFutures(Throwable failure) {
        List<CompletableFuture<?>> runningFutures;
        synchronized (lock) {
            runningFutures = new ArrayList<>();
            for (Operation<?> operation : operations) {
                if (operation.status == OperationStatus.RUNNING) {
                    runningFutures.add(operation.future);
                }
            }
        }
        runningFutures.forEach(future -> future.completeExceptionally(failure));
    }

    private boolean stopIfDrainedLocked() {
        if (state == State.SHUTTING_DOWN && operations.isEmpty()) {
            state = State.STOPPED;
            return true;
        }
        return false;
    }

    private enum OperationStatus {
        QUEUED,
        RUNNING,
        FINISHED,
        CANCELLED
    }

    private final class Operation<T> implements Runnable {
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private OperationStatus status = OperationStatus.QUEUED;

        private Operation(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void run() {
            synchronized (lock) {
                if (status != OperationStatus.QUEUED || state != State.ACCEPTING) {
                    return;
                }
                status = OperationStatus.RUNNING;
            }

            T result = null;
            Throwable failure = null;
            try {
                result = supplier.get();
            } catch (Throwable error) {
                failure = error;
            }

            boolean stopped;
            synchronized (lock) {
                status = OperationStatus.FINISHED;
                operations.remove(this);
                stopped = stopIfDrainedLocked();
            }
            if (stopped) {
                termination.complete(null);
            }
            if (failure == null) {
                future.complete(result);
            } else {
                future.completeExceptionally(failure);
            }
        }
    }
}
