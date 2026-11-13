package com.xiaoyiluck.meoweco.utils;

import java.util.Optional;

public final class UpdateCheckerRegressionTest {
    private UpdateCheckerRegressionTest() {
    }

    public static void main(String[] args) {
        ignoresOtherMinecraftBranches();
        selectsHighestCompatibleVersion();
        requiresCompatibleMetadata();
        comparesReleaseComponents();
        System.out.println("Update checker regression tests passed.");
    }

    private static void ignoresOtherMinecraftBranches() {
        // Reproduce the actual Modrinth ordering: the 1.21.11 backport was uploaded last.
        String releases = """
                [
                  {"version_number":"26.10.7.1-mc1.21.11","game_versions":["1.21.11"],"loaders":["paper","purpur"]},
                  {"version_number":"26.10.7","game_versions":["26.1.1","26.1.2","26.2","26.3"],"loaders":["paper","purpur"]}
                ]
                """;
        for (String minecraft : new String[]{"26.1.1", "26.1.2", "26.2", "26.3"}) {
            assertEquals(Optional.empty(), UpdateChecker.findCompatibleUpdate(releases, minecraft, "26.10.7+maowo.2"));
        }
        assertEquals(Optional.of("26.10.7.1-mc1.21.11"),
                UpdateChecker.findCompatibleUpdate(releases, "1.21.11", "26.10.7-mc1.21.11"));
        assertEquals(Optional.of("26.10.7"),
                UpdateChecker.findCompatibleUpdate(releases, "26.2", "26.10.6"));
    }

    private static void selectsHighestCompatibleVersion() {
        String releases = """
                [
                  {"version_number":"26.10.8","game_versions":["26.2"],"loaders":["paper"]},
                  {"version_number":"99.0.0","game_versions":["26.2"],"loaders":["fabric"]},
                  {"version_number":"26.11.0","game_versions":["26.2"],"loaders":["paper","purpur"]},
                  {"version_number":"26.10.9","game_versions":["26.2"],"loaders":["paper"]}
                ]
                """;
        assertEquals(Optional.of("26.11.0"),
                UpdateChecker.findCompatibleUpdate(releases, "26.2", "26.10.7+maowo.2"));
        assertEquals(Optional.empty(), UpdateChecker.findCompatibleUpdate(releases, "26.2", "26.11.0"));
        assertEquals(Optional.empty(), UpdateChecker.findCompatibleUpdate(releases, "26.3", "26.10.7"));
    }

    private static void requiresCompatibleMetadata() {
        String releases = """
                [
                  null, 5, {},
                  {"version_number":"99.0","loaders":["paper"]},
                  {"version_number":"99.0","game_versions":["26.2"]},
                  {"version_number":"99.0","game_versions":"26.2","loaders":["paper"]},
                  {"version_number":"99.0","game_versions":["26.2"],"loaders":null},
                  {"version_number":99,"game_versions":["26.2"],"loaders":["paper"]},
                  {"version_number":"invalid","game_versions":["26.2"],"loaders":["paper"]}
                ]
                """;
        assertEquals(Optional.empty(), UpdateChecker.findCompatibleUpdate(releases, "26.2", "26.10.7"));
        assertEquals(Optional.empty(), UpdateChecker.findCompatibleUpdate("[]", "26.2", "26.10.7"));
    }

    private static void comparesReleaseComponents() {
        assertNewer(true, "26.10.7", "26.9.99");
        assertNewer(false, "26.9.99", "26.10.7");
        assertNewer(true, "26.10.8", "26.10.7+maowo.2");
        assertNewer(false, "26.10.7", "26.10.7+maowo.2");
        assertNewer(false, "v26.10.7.0", "26.10.7");
        assertNewer(true, "v26.10.7.1-mc1.21.11", "26.10.7-mc1.21.11");
        assertNewer(false, "1.21.11", "26.10.7");
        assertNewer(false, "invalid", "26.10.7");
        assertNewer(false, "26.10.8", "unknown");
    }

    private static void assertNewer(boolean expected, String candidate, String current) {
        assertEquals(expected, UpdateChecker.isNewer(candidate, current));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
