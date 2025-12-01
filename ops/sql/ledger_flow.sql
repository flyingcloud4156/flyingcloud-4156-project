-- ------------------------------------------------------------
-- Global SQL mode & engine hints (adjust as needed)
-- ------------------------------------------------------------
-- MySQL 8.0+ is assumed.
-- Use strict mode for safer money handling and foreign keys.
-- ------------------------------------------
-- Reset existing tables if they exist
-- ------------------------------------------

-- 如果数据库不存在就建一个
CREATE DATABASE IF NOT EXISTS ledger
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE ledger;

DROP TABLE IF EXISTS
    budgets,
    attachments,
    settlements,
    ledger_user_balances,
    debt_edges,
    transaction_splits,
    transactions,
    categories,
    ledger_members,
    ledgers,
    users,
    currency,
    import_jobs;

SET sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- ------------------------------------------------------------
-- Helper: currencies (minimal, with USD default elsewhere)
-- ------------------------------------------------------------
CREATE TABLE currency (
                          code            CHAR(3) PRIMARY KEY, -- ISO-4217 code, e.g., 'USD'
                          exponent        TINYINT NOT NULL,    -- minor unit exponent (USD=2, JPY=0, etc.)
                          CONSTRAINT ck_currency_exponent CHECK (exponent BETWEEN 0 AND 6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Supported currencies and their minor unit exponents. Minimal helper table.';

INSERT INTO currency (code, exponent) VALUES
    ('USD', 2) -- Default currency for ledgers
    ON DUPLICATE KEY UPDATE exponent = VALUES(exponent);

-- ------------------------------------------------------------
-- Users
-- ------------------------------------------------------------
CREATE TABLE users (
                       id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                       name            VARCHAR(80) NOT NULL COMMENT 'Displayable user name; keep it simple.',
                       email           VARCHAR(255) NULL UNIQUE COMMENT 'Optional login identifier.',
                       phone           VARCHAR(32) NULL UNIQUE COMMENT 'Optional login identifier. E.164 if used.',
                       password_hash   VARCHAR(255) NOT NULL COMMENT 'Hashed secret; exact hashing handled in app.',
                       timezone        VARCHAR(64) NOT NULL DEFAULT 'America/New_York' COMMENT 'IANA timezone.',
                       main_currency   CHAR(3) NOT NULL DEFAULT 'USD' COMMENT 'User’s preferred currency display.',
                       created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       CONSTRAINT fk_users_currency FOREIGN KEY (main_currency) REFERENCES currency(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='App users. Keep fields minimal by design.';

-- ------------------------------------------------------------
-- Ledgers (aka "books" or "accounts") supporting three modes
-- ------------------------------------------------------------
CREATE TABLE ledgers (
                         id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                         name            VARCHAR(120) NOT NULL COMMENT 'Ledger name (e.g., Family 2025, Trip to Japan).',
                         owner_id        BIGINT UNSIGNED NOT NULL COMMENT 'Primary owner; also a member with OWNER role.',
                         ledger_type     ENUM('SINGLE','GROUP_BALANCE','DEBT_NETWORK') NOT NULL DEFAULT 'GROUP_BALANCE'
                      COMMENT 'Modes: SINGLE (solo), GROUP_BALANCE (family-style net), DEBT_NETWORK (explicit A->B loans).',
                         base_currency   CHAR(3) NOT NULL DEFAULT 'USD'
                             COMMENT 'Ledger’s accounting currency. All balances reported in this.',
                         share_start_date DATE NULL
                      COMMENT 'Only records on/after this date are visible to members (privacy boundary).',
                         created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         CONSTRAINT fk_ledgers_owner FOREIGN KEY (owner_id) REFERENCES users(id),
                         CONSTRAINT fk_ledgers_currency FOREIGN KEY (base_currency) REFERENCES currency(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Logical books for SINGLE, GROUP_BALANCE, and DEBT_NETWORK modes. Default currency is USD.';

-- ------------------------------------------------------------
-- Ledger membership & roles
-- ------------------------------------------------------------
CREATE TABLE ledger_members (
                                ledger_id       BIGINT UNSIGNED NOT NULL,
                                user_id         BIGINT UNSIGNED NOT NULL,
                                role            ENUM('OWNER','ADMIN','EDITOR','VIEWER') NOT NULL DEFAULT 'EDITOR'
                      COMMENT 'OWNER has full control; ADMIN manages members; EDITOR can add/modify own records; VIEWER is read-only.',
                                joined_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (ledger_id, user_id),
                                CONSTRAINT fk_members_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                                CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Memberships & permissions per ledger.';

-- ------------------------------------------------------------
-- Categories (per ledger, shared vocabulary)
-- ------------------------------------------------------------
CREATE TABLE categories (
                            id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                            ledger_id       BIGINT UNSIGNED NOT NULL,
                            name            VARCHAR(80) NOT NULL,
                            kind            ENUM('EXPENSE','INCOME','TRANSFER') NOT NULL DEFAULT 'EXPENSE'
                      COMMENT 'Used for basic grouping; transactions still carry their own type.',
                            is_active       BOOLEAN NOT NULL DEFAULT TRUE,
                            sort_order      INT NOT NULL DEFAULT 0,
                            UNIQUE KEY uk_ledger_name (ledger_id, name),
                            CONSTRAINT fk_categories_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Simple per-ledger category list for analytics.';

-- ------------------------------------------------------------
-- Transactions (single source of truth for money events)
-- ------------------------------------------------------------
CREATE TABLE transactions (
                              id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                              ledger_id       BIGINT UNSIGNED NOT NULL,
                              created_by      BIGINT UNSIGNED NOT NULL COMMENT 'User who created the record.',
                              txn_at          DATETIME NOT NULL COMMENT 'When the transaction happened.',
                              type            ENUM('EXPENSE','INCOME','TRANSFER','LOAN') NOT NULL DEFAULT 'EXPENSE'
                      COMMENT 'EXPENSE/INCOME for regular flow; TRANSFER for internal moves; LOAN for A->B lending.',
                              category_id     BIGINT UNSIGNED NULL,
                              payer_id        BIGINT UNSIGNED NULL
                      COMMENT 'Who paid upfront (for shared bills / reimbursements). May be NULL for imported entries.',
                              amount_total    DECIMAL(20,8) NOT NULL
                                  COMMENT 'Transaction gross amount in its native currency.',
                              currency        CHAR(3) NOT NULL DEFAULT 'USD',
                              note            VARCHAR(500) NULL,
                              is_private      BOOLEAN NOT NULL DEFAULT FALSE
                                  COMMENT 'If TRUE, visible only to creator until/unless ownership rules override.',
                              rounding_strategy ENUM('NONE','ROUND_HALF_UP','TRIM_TO_UNIT') NOT NULL DEFAULT 'ROUND_HALF_UP'
                      COMMENT 'How to round per-participant shares.',
                              tail_allocation ENUM('PAYER','LARGEST_SHARE','CREATOR') NOT NULL DEFAULT 'PAYER'
                      COMMENT 'Who absorbs the rounding remainder.',
                              created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              CONSTRAINT fk_txn_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                              CONSTRAINT fk_txn_creator FOREIGN KEY (created_by) REFERENCES users(id),
                              CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES categories(id),
                              CONSTRAINT fk_txn_payer FOREIGN KEY (payer_id) REFERENCES users(id),
                              CONSTRAINT fk_txn_currency FOREIGN KEY (currency) REFERENCES currency(code),
                              INDEX idx_txn_ledger_time (ledger_id, txn_at),
                              INDEX idx_txn_type (ledger_id, type, txn_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Transactions are the canonical records. Splits/participants and debt edges reference this.';

-- ------------------------------------------------------------
-- Participants / Splits (who shares how much of a transaction)
-- ------------------------------------------------------------
CREATE TABLE transaction_splits (
                                    id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                                    transaction_id  BIGINT UNSIGNED NOT NULL,
                                    user_id         BIGINT UNSIGNED NOT NULL,
                                    split_method    ENUM('EQUAL','PERCENT','WEIGHT','EXACT') NOT NULL
                      COMMENT 'EQUAL: all equal parts; PERCENT: share_value is percent (0-100); WEIGHT: relative weight; EXACT: share_value is absolute amount.',
                                    share_value     DECIMAL(20,8) NOT NULL DEFAULT 0
                                        COMMENT 'Meaning depends on split_method. For EXACT it is the final amount in transaction currency.',
                                    included        BOOLEAN NOT NULL DEFAULT TRUE
                                        COMMENT 'FALSE means explicitly excluded from this transaction.',
                                    computed_amount DECIMAL(20,8) NULL
                      COMMENT 'Optional: materialize final per-user amount in transaction currency after rounding.',
                                    UNIQUE KEY uk_split_txn_user (transaction_id, user_id),
                                    CONSTRAINT fk_splits_txn FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_splits_user FOREIGN KEY (user_id) REFERENCES users(id),
                                    INDEX idx_splits_txn (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Participant-level shares for a transaction. Supports equal, percentage, weight, or exact allocations.';

-- ------------------------------------------------------------
-- Debt edges (direct "A owes B" relations)
-- ------------------------------------------------------------
CREATE TABLE debt_edges (
                            id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                            ledger_id       BIGINT UNSIGNED NOT NULL,
                            transaction_id  BIGINT UNSIGNED NULL
                      COMMENT 'If generated from a transaction, reference it; NULL for ad-hoc/loan entries.',
                            from_user_id    BIGINT UNSIGNED NOT NULL
                      COMMENT 'Creditor (the one who is owed money).',
                            to_user_id      BIGINT UNSIGNED NOT NULL
                      COMMENT 'Debtor (the one who owes).',
                            amount          DECIMAL(20,8) NOT NULL
                                COMMENT 'Positive amount in edge_currency.',
                            edge_currency   CHAR(3) NOT NULL DEFAULT 'USD',
                            created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE KEY uk_debt_unique (ledger_id, transaction_id, from_user_id, to_user_id),
                            CONSTRAINT fk_debt_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                            CONSTRAINT fk_debt_txn FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL,
                            CONSTRAINT fk_debt_from FOREIGN KEY (from_user_id) REFERENCES users(id),
                            CONSTRAINT fk_debt_to FOREIGN KEY (to_user_id) REFERENCES users(id),
                            CONSTRAINT fk_debt_currency FOREIGN KEY (edge_currency) REFERENCES currency(code),
                            CONSTRAINT ck_debt_positive CHECK (amount > 0),
                            INDEX idx_debt_ledger_parties (ledger_id, to_user_id, from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Directed debts A->B. Used by DEBT_NETWORK and by simplified edges from shared bills for settlement optimization.';

-- ------------------------------------------------------------
-- Ledger balances (materialized per-user net in base currency)
-- ------------------------------------------------------------
CREATE TABLE ledger_user_balances (
                                      ledger_id       BIGINT UNSIGNED NOT NULL,
                                      user_id         BIGINT UNSIGNED NOT NULL,
                                      net_amount_base DECIMAL(20,8) NOT NULL
                                          COMMENT 'Net receivable (+) or payable (–) in ledger base currency.',
                                      recalculated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (ledger_id, user_id),
                                      CONSTRAINT fk_bal_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_bal_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Snapshot of per-user net balances for GROUP_BALANCE view; recompute after changes.';

-- ------------------------------------------------------------
-- Settlement transfers (executed minimal-transfer solutions)
-- ------------------------------------------------------------
CREATE TABLE settlements (
                             id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                             ledger_id       BIGINT UNSIGNED NOT NULL,
                             from_user_id    BIGINT UNSIGNED NOT NULL,
                             to_user_id      BIGINT UNSIGNED NOT NULL,
                             amount          DECIMAL(20,8) NOT NULL,
                             currency        CHAR(3) NOT NULL DEFAULT 'USD',
                             method          ENUM('CASH','BANK','THIRD_PARTY','OTHER') NOT NULL DEFAULT 'OTHER',
                             settled_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             note            VARCHAR(500) NULL,
                             plan_batch_id   BIGINT UNSIGNED NULL
                      COMMENT 'Optional: link multiple settlement rows that were executed together.',
                             CONSTRAINT fk_settle_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                             CONSTRAINT fk_settle_from FOREIGN KEY (from_user_id) REFERENCES users(id),
                             CONSTRAINT fk_settle_to FOREIGN KEY (to_user_id) REFERENCES users(id),
                             CONSTRAINT fk_settle_currency FOREIGN KEY (currency) REFERENCES currency(code),
                             CONSTRAINT ck_settle_positive CHECK (amount > 0),
                             INDEX idx_settle_ledger_time (ledger_id, settled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Actual transfers recorded when settling up. Supports grouping via plan_batch_id.';

-- ------------------------------------------------------------
-- Attachments (receipts, images, etc.) with light OCR support
-- ------------------------------------------------------------
CREATE TABLE attachments (
                             id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                             transaction_id  BIGINT UNSIGNED NOT NULL,
                             file_url        VARCHAR(1024) NOT NULL COMMENT 'Where the file is stored (S3/GCS/etc.).',
                             mime_type       VARCHAR(100) NOT NULL,
                             ocr_text        MEDIUMTEXT NULL COMMENT 'Optional raw OCR text for later extraction.',
                             created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_att_txn FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
                             INDEX idx_att_txn (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Linked files for transactions. OCR text is kept simple for AI-assisted entry.';

-- ------------------------------------------------------------
-- Optional: lightweight import jobs (for “AI / OCR” pipeline)
-- ------------------------------------------------------------
CREATE TABLE import_jobs (
                             id              BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                             user_id         BIGINT UNSIGNED NOT NULL,
                             ledger_id       BIGINT UNSIGNED NULL,
                             source_type     ENUM('TEXT','IMAGE','RECEIPT','CSV') NOT NULL,
                             status          ENUM('PENDING','PARSED','CONFIRMED','DISCARDED') NOT NULL DEFAULT 'PENDING',
                             raw_payload     MEDIUMTEXT NULL COMMENT 'Original text or metadata blob. Do NOT store binaries here.',
                             parsed_json     JSON NULL COMMENT 'Extracted candidates: amount, currency, datetime, category, participants, etc.',
                             created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             CONSTRAINT fk_imp_user FOREIGN KEY (user_id) REFERENCES users(id),
                             CONSTRAINT fk_imp_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id),
                             INDEX idx_imp_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Minimal ingestion records for AI/OCR/CSV-assisted bookkeeping.';

-- ------------------------------------------------------------
-- Budgets (new) - monthly / category budgets per ledger
-- ------------------------------------------------------------
CREATE TABLE budgets (
                         id           BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
                         ledger_id    BIGINT UNSIGNED NOT NULL,
                         category_id  BIGINT UNSIGNED NULL COMMENT 'NULL = whole-ledger budget; non-NULL = per-category budget',
                         year         INT NOT NULL,
                         month        TINYINT NOT NULL,
                         limit_amount DECIMAL(20,8) NOT NULL,
                         created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         UNIQUE KEY uk_budget_scope (ledger_id, category_id, year, month),
                         CONSTRAINT fk_budget_ledger FOREIGN KEY (ledger_id) REFERENCES ledgers(id) ON DELETE CASCADE,
                         CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Monthly budgets per ledger/category. Used only for budget checks; settlements do not change budgets.';

-- ------------------------------------------------------------
-- Practical indexes for analytics
-- ------------------------------------------------------------
CREATE INDEX idx_txn_ledger_category_time ON transactions (ledger_id, category_id, txn_at);
CREATE INDEX idx_txn_creator_time ON transactions (created_by, txn_at);

-- ------------------------------------------------------------
-- Notes for implementers (not SQL):
-- - Money columns use DECIMAL(20,8) for safety; display/round according to currency.exponent.
-- - For GROUP_BALANCE, compute debt_edges from transaction_splits (payer vs. participants),
--   then net per-user into ledger_user_balances. Settlement suggestions (min transfer)
--   are computed in code, but recorded in settlements when executed.
-- - Budgets use ledger.base_currency implicitly; do NOT double-count settlements in budget.
-- - Visibility:
--     * transactions.is_private hides the record except creator (and owner/admin if you choose).
--     * ledgers.share_start_date hides older data from non-owners.
-- - Default currency is USD across ledgers and transactions; multi-currency is supported
--   via currency table and per-row currency fields.

