package dev.coms4156.project.groupproject.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ops/sql/backup/ledger_big_seed.sql.
 *
 * <p>This is a FILE-LEVEL contract test (no DB connection): we assert the seed file contains
 * essential operations and key fixtures used by the analytics demo (two ledgers, members,
 * categories, transactions).
 */
class LedgerBigSeedSqlFileTest {

  @Test
  void bigSeedFile_shouldExistAndContainKeySections() throws Exception {
    Path p = Path.of("ops", "sql", "backup", "ledger_big_seed.sql");
    assertTrue(Files.exists(p), "Expected ledger_big_seed.sql to exist at " + p.toAbsolutePath());

    String s = Files.readString(p, StandardCharsets.UTF_8);

    // Core safety: truncations exist
    assertTrue(s.contains("TRUNCATE TABLE transactions;"));
    assertTrue(s.contains("TRUNCATE TABLE transaction_splits;"));
    assertTrue(s.contains("TRUNCATE TABLE debt_edges;"));

    // Must create two demo ledgers with stable names & IDs
    assertTrue(s.contains("INSERT INTO ledgers"));
    assertTrue(s.contains("(1,'Road Trip Demo'"));
    assertTrue(s.contains("(2,'Apartment Demo'"));

    // Must have categories inserted
    assertTrue(s.contains("INSERT INTO categories"));

    // Must have transactions for both ledgers in recent months (for charts)
    assertTrue(s.contains("INSERT INTO transactions"));
    assertTrue(s.contains("'2025-09-03 10:00:00'"));
    assertTrue(s.contains("'Rent Sep'"));

    // Must include debt edges (for AR/AP)
    assertTrue(s.contains("INSERT INTO debt_edges"));
  }
}
