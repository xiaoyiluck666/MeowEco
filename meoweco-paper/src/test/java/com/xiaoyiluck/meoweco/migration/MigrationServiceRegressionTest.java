package com.xiaoyiluck.meoweco.migration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MigrationServiceRegressionTest {
    private MigrationServiceRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        parsesQuotedCsvFields();
        importsCommonCsvColumnAliasesAndSkipsInvalidRows();
    }

    private static void parsesQuotedCsvFields() {
        List<String> values = MigrationService.parseCsvLine("uuid,\"Player, One\",\"12\"\"34\"");
        assertEquals(List.of("uuid", "Player, One", "12\"34"), values);
    }

    private static void importsCommonCsvColumnAliasesAndSkipsInvalidRows() throws Exception {
        Path csv = Files.createTempFile("meoweco-migration-", ".csv");
        try {
            Files.writeString(csv, "\uFEFFuuid,player,money\n"
                    + "d290f1ee-6c54-4b01-90e6-d701748f0851,Alice,12.5\n"
                    + "not-a-uuid,Broken,50\n"
                    + "2c1e4b10-a31f-43c2-8ff7-2beed389533c,Negative,-1\n",
                    StandardCharsets.UTF_8);

            MigrationPreview preview = new MigrationService(null).previewCsv(csv);

            assertInt(1, preview.balances().size());
            assertInt(2, preview.skippedEntries());
            assertDouble(12.5D, preview.totalBalance());
            assertEquals("Alice", preview.balances().get(0).username());
        } finally {
            Files.deleteIfExists(csv);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertDouble(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001D) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertInt(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
