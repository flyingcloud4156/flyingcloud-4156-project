/*
 Navicat Premium Data Transfer

 Source Server         : localconnnect
 Source Server Type    : MySQL
 Source Server Version : 90300
 Source Host           : localhost:3306
 Source Schema         : ledger

 Target Server Type    : MySQL
 Target Server Version : 90300
 File Encoding         : 65001

 Date: 21/10/2025 19:43:27
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for attachments
-- ----------------------------
DROP TABLE IF EXISTS `attachments`;
CREATE TABLE `attachments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `transaction_id` bigint unsigned NOT NULL,
  `file_url` varchar(1024) NOT NULL COMMENT 'Where the file is stored (S3/GCS/etc.).',
  `mime_type` varchar(100) NOT NULL,
  `ocr_text` mediumtext COMMENT 'Optional raw OCR text for later extraction.',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_att_txn` (`transaction_id`),
  CONSTRAINT `fk_att_txn` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Linked files for transactions. OCR text is kept simple for AI-assisted entry.';

-- ----------------------------
-- Records of attachments
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for categories
-- ----------------------------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_id` bigint unsigned NOT NULL,
  `name` varchar(80) NOT NULL,
  `kind` enum('EXPENSE','INCOME','TRANSFER') NOT NULL DEFAULT 'EXPENSE' COMMENT 'Used for basic grouping; transactions still carry their own type.',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_name` (`ledger_id`,`name`),
  CONSTRAINT `fk_categories_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Simple per-ledger category list for analytics.';

-- ----------------------------
-- Records of categories
-- ----------------------------
BEGIN;
INSERT INTO `categories` (`id`, `ledger_id`, `name`, `kind`, `is_active`, `sort_order`) VALUES (1, 1, 'Groceries', 'EXPENSE', 1, 1);
INSERT INTO `categories` (`id`, `ledger_id`, `name`, `kind`, `is_active`, `sort_order`) VALUES (2, 1, 'MYGroceries', 'EXPENSE', 1, 1);
COMMIT;

-- ----------------------------
-- Table structure for currency
-- ----------------------------
DROP TABLE IF EXISTS `currency`;
CREATE TABLE `currency` (
  `code` char(3) NOT NULL,
  `exponent` tinyint NOT NULL,
  PRIMARY KEY (`code`),
  CONSTRAINT `ck_currency_exponent` CHECK ((`exponent` between 0 and 6))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Supported currencies and their minor unit exponents. Minimal helper table.';

-- ----------------------------
-- Records of currency
-- ----------------------------
BEGIN;
INSERT INTO `currency` (`code`, `exponent`) VALUES ('USD', 2);
COMMIT;

-- ----------------------------
-- Table structure for debt_edges
-- ----------------------------
DROP TABLE IF EXISTS `debt_edges`;
CREATE TABLE `debt_edges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_id` bigint unsigned NOT NULL,
  `transaction_id` bigint unsigned DEFAULT NULL COMMENT 'If generated from a transaction, reference it; NULL for ad-hoc/loan entries.',
  `from_user_id` bigint unsigned NOT NULL COMMENT 'Creditor (the one who is owed money).',
  `to_user_id` bigint unsigned NOT NULL COMMENT 'Debtor (the one who owes).',
  `amount` decimal(20,8) NOT NULL COMMENT 'Positive amount in edge_currency.',
  `edge_currency` char(3) NOT NULL DEFAULT 'USD',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_debt_unique` (`ledger_id`,`transaction_id`,`from_user_id`,`to_user_id`),
  KEY `fk_debt_txn` (`transaction_id`),
  KEY `fk_debt_from` (`from_user_id`),
  KEY `fk_debt_to` (`to_user_id`),
  KEY `fk_debt_currency` (`edge_currency`),
  KEY `idx_debt_ledger_parties` (`ledger_id`,`to_user_id`,`from_user_id`),
  CONSTRAINT `fk_debt_currency` FOREIGN KEY (`edge_currency`) REFERENCES `currency` (`code`),
  CONSTRAINT `fk_debt_from` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_debt_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_debt_to` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_debt_txn` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `ck_debt_positive` CHECK ((`amount` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Directed debts A->B. Used by DEBT_NETWORK and by simplified edges from shared bills for settlement optimization.';

-- ----------------------------
-- Records of debt_edges
-- ----------------------------
BEGIN;
INSERT INTO `debt_edges` (`id`, `ledger_id`, `transaction_id`, `from_user_id`, `to_user_id`, `amount`, `edge_currency`, `created_at`) VALUES (1, 6, 1, 4, 5, 30.00000000, 'USD', '2025-10-21 19:33:17');
INSERT INTO `debt_edges` (`id`, `ledger_id`, `transaction_id`, `from_user_id`, `to_user_id`, `amount`, `edge_currency`, `created_at`) VALUES (2, 6, 1, 4, 6, 30.00000000, 'USD', '2025-10-21 19:33:17');
INSERT INTO `debt_edges` (`id`, `ledger_id`, `transaction_id`, `from_user_id`, `to_user_id`, `amount`, `edge_currency`, `created_at`) VALUES (3, 6, 2, 6, 4, 25.00000000, 'USD', '2025-10-21 19:33:43');
COMMIT;

-- ----------------------------
-- Table structure for import_jobs
-- ----------------------------
DROP TABLE IF EXISTS `import_jobs`;
CREATE TABLE `import_jobs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `ledger_id` bigint unsigned DEFAULT NULL,
  `source_type` enum('TEXT','IMAGE','RECEIPT','CSV') NOT NULL,
  `status` enum('PENDING','PARSED','CONFIRMED','DISCARDED') NOT NULL DEFAULT 'PENDING',
  `raw_payload` mediumtext COMMENT 'Original text or metadata blob. Do NOT store binaries here.',
  `parsed_json` json DEFAULT NULL COMMENT 'Extracted candidates: amount, currency, datetime, category, participants, etc.',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_imp_ledger` (`ledger_id`),
  KEY `idx_imp_user_time` (`user_id`,`created_at`),
  CONSTRAINT `fk_imp_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`),
  CONSTRAINT `fk_imp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Minimal ingestion records for AI/OCR/CSV-assisted bookkeeping.';

-- ----------------------------
-- Records of import_jobs
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for ledger_members
-- ----------------------------
DROP TABLE IF EXISTS `ledger_members`;
CREATE TABLE `ledger_members` (
  `ledger_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `role` enum('OWNER','ADMIN','EDITOR','VIEWER') NOT NULL DEFAULT 'EDITOR' COMMENT 'OWNER has full control; ADMIN manages members; EDITOR can add/modify own records; VIEWER is read-only.',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ledger_id`,`user_id`),
  KEY `fk_members_user` (`user_id`),
  CONSTRAINT `fk_members_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Memberships & permissions per ledger.';

-- ----------------------------
-- Records of ledger_members
-- ----------------------------
BEGIN;
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (1, 1, 'OWNER', '2025-10-16 23:23:58');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (2, 4, 'OWNER', '2025-10-21 19:12:39');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (3, 4, 'OWNER', '2025-10-21 19:17:18');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (4, 4, 'OWNER', '2025-10-21 19:17:56');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (5, 4, 'OWNER', '2025-10-21 19:18:19');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (6, 4, 'OWNER', '2025-10-21 19:30:59');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (6, 5, 'VIEWER', '2025-10-21 19:31:07');
INSERT INTO `ledger_members` (`ledger_id`, `user_id`, `role`, `joined_at`) VALUES (6, 6, 'EDITOR', '2025-10-21 19:31:04');
COMMIT;

-- ----------------------------
-- Table structure for ledger_user_balances
-- ----------------------------
DROP TABLE IF EXISTS `ledger_user_balances`;
CREATE TABLE `ledger_user_balances` (
  `ledger_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `net_amount_base` decimal(20,8) NOT NULL COMMENT 'Net receivable (+) or payable (–) in ledger base currency.',
  `recalculated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ledger_id`,`user_id`),
  KEY `fk_bal_user` (`user_id`),
  CONSTRAINT `fk_bal_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bal_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Snapshot of per-user net balances for GROUP_BALANCE view; recompute after changes.';

-- ----------------------------
-- Records of ledger_user_balances
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for ledgers
-- ----------------------------
DROP TABLE IF EXISTS `ledgers`;
CREATE TABLE `ledgers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(120) NOT NULL COMMENT 'Ledger name (e.g., Family 2025, Trip to Japan).',
  `owner_id` bigint unsigned NOT NULL COMMENT 'Primary owner; also a member with OWNER role.',
  `ledger_type` enum('SINGLE','GROUP_BALANCE','DEBT_NETWORK') NOT NULL DEFAULT 'GROUP_BALANCE' COMMENT 'Modes: SINGLE (solo), GROUP_BALANCE (family-style net), DEBT_NETWORK (explicit A->B loans).',
  `base_currency` char(3) NOT NULL DEFAULT 'USD' COMMENT 'Ledger’s accounting currency. All balances reported in this.',
  `share_start_date` date DEFAULT NULL COMMENT 'Only records on/after this date are visible to members (privacy boundary).',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_ledgers_owner` (`owner_id`),
  KEY `fk_ledgers_currency` (`base_currency`),
  CONSTRAINT `fk_ledgers_currency` FOREIGN KEY (`base_currency`) REFERENCES `currency` (`code`),
  CONSTRAINT `fk_ledgers_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Logical books for SINGLE, GROUP_BALANCE, and DEBT_NETWORK modes. Default currency is USD.';

-- ----------------------------
-- Records of ledgers
-- ----------------------------
BEGIN;
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (1, 'Family Ledger 2025', 1, 'GROUP_BALANCE', 'USD', '2025-10-17', '2025-10-16 23:23:58', '2025-10-16 23:23:58');
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (2, 'Road Trip 2025', 4, 'GROUP_BALANCE', 'USD', NULL, '2025-10-21 19:12:39', '2025-10-21 19:12:39');
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (3, 'Road Trip 2025', 4, 'GROUP_BALANCE', 'USD', NULL, '2025-10-21 19:17:18', '2025-10-21 19:17:18');
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (4, 'Road Trip 2025', 4, 'GROUP_BALANCE', 'USD', NULL, '2025-10-21 19:17:56', '2025-10-21 19:17:56');
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (5, 'Road Trip 2025', 4, 'GROUP_BALANCE', 'USD', NULL, '2025-10-21 19:18:19', '2025-10-21 19:18:19');
INSERT INTO `ledgers` (`id`, `name`, `owner_id`, `ledger_type`, `base_currency`, `share_start_date`, `created_at`, `updated_at`) VALUES (6, 'Road Trip 2025', 4, 'GROUP_BALANCE', 'USD', NULL, '2025-10-21 19:30:59', '2025-10-21 19:30:59');
COMMIT;

-- ----------------------------
-- Table structure for settlements
-- ----------------------------
DROP TABLE IF EXISTS `settlements`;
CREATE TABLE `settlements` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_id` bigint unsigned NOT NULL,
  `from_user_id` bigint unsigned NOT NULL,
  `to_user_id` bigint unsigned NOT NULL,
  `amount` decimal(20,8) NOT NULL,
  `currency` char(3) NOT NULL DEFAULT 'USD',
  `method` enum('CASH','BANK','THIRD_PARTY','OTHER') NOT NULL DEFAULT 'OTHER',
  `settled_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `note` varchar(500) DEFAULT NULL,
  `plan_batch_id` bigint unsigned DEFAULT NULL COMMENT 'Optional: link multiple settlement rows that were executed together.',
  PRIMARY KEY (`id`),
  KEY `fk_settle_from` (`from_user_id`),
  KEY `fk_settle_to` (`to_user_id`),
  KEY `fk_settle_currency` (`currency`),
  KEY `idx_settle_ledger_time` (`ledger_id`,`settled_at`),
  CONSTRAINT `fk_settle_currency` FOREIGN KEY (`currency`) REFERENCES `currency` (`code`),
  CONSTRAINT `fk_settle_from` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_settle_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_settle_to` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `ck_settle_positive` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Actual transfers recorded when settling up. Supports grouping via plan_batch_id.';

-- ----------------------------
-- Records of settlements
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for transaction_splits
-- ----------------------------
DROP TABLE IF EXISTS `transaction_splits`;
CREATE TABLE `transaction_splits` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `transaction_id` bigint unsigned NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `split_method` enum('EQUAL','PERCENT','WEIGHT','EXACT') NOT NULL COMMENT 'EQUAL: all equal parts; PERCENT: share_value is percent (0-100); WEIGHT: relative weight; EXACT: share_value is absolute amount.',
  `share_value` decimal(20,8) NOT NULL DEFAULT '0.00000000' COMMENT 'Meaning depends on split_method. For EXACT it is the final amount in transaction currency.',
  `included` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'FALSE means explicitly excluded from this transaction.',
  `computed_amount` decimal(20,8) DEFAULT NULL COMMENT 'Optional: materialize final per-user amount in transaction currency after rounding.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_split_txn_user` (`transaction_id`,`user_id`),
  KEY `fk_splits_user` (`user_id`),
  KEY `idx_splits_txn` (`transaction_id`),
  CONSTRAINT `fk_splits_txn` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_splits_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Participant-level shares for a transaction. Supports equal, percentage, weight, or exact allocations.';

-- ----------------------------
-- Records of transaction_splits
-- ----------------------------
BEGIN;
INSERT INTO `transaction_splits` (`id`, `transaction_id`, `user_id`, `split_method`, `share_value`, `included`, `computed_amount`) VALUES (1, 1, 4, 'EQUAL', 0.00000000, 1, 30.00000000);
INSERT INTO `transaction_splits` (`id`, `transaction_id`, `user_id`, `split_method`, `share_value`, `included`, `computed_amount`) VALUES (2, 1, 6, 'EQUAL', 0.00000000, 1, 30.00000000);
INSERT INTO `transaction_splits` (`id`, `transaction_id`, `user_id`, `split_method`, `share_value`, `included`, `computed_amount`) VALUES (3, 1, 5, 'EQUAL', 0.00000000, 1, 30.00000000);
INSERT INTO `transaction_splits` (`id`, `transaction_id`, `user_id`, `split_method`, `share_value`, `included`, `computed_amount`) VALUES (4, 2, 4, 'EXACT', 25.00000000, 1, 25.00000000);
INSERT INTO `transaction_splits` (`id`, `transaction_id`, `user_id`, `split_method`, `share_value`, `included`, `computed_amount`) VALUES (5, 2, 6, 'EXACT', 35.00000000, 1, 35.00000000);
COMMIT;

-- ----------------------------
-- Table structure for transactions
-- ----------------------------
DROP TABLE IF EXISTS `transactions`;
CREATE TABLE `transactions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_id` bigint unsigned NOT NULL,
  `created_by` bigint unsigned NOT NULL COMMENT 'User who created the record.',
  `txn_at` datetime NOT NULL COMMENT 'When the transaction happened.',
  `type` enum('EXPENSE','INCOME','TRANSFER','LOAN') NOT NULL DEFAULT 'EXPENSE' COMMENT 'EXPENSE/INCOME for regular flow; TRANSFER for internal moves; LOAN for A->B lending.',
  `category_id` bigint unsigned DEFAULT NULL,
  `payer_id` bigint unsigned DEFAULT NULL COMMENT 'Who paid upfront (for shared bills / reimbursements). May be NULL for imported entries.',
  `amount_total` decimal(20,8) NOT NULL COMMENT 'Transaction gross amount in its native currency.',
  `currency` char(3) NOT NULL DEFAULT 'USD',
  `note` varchar(500) DEFAULT NULL,
  `is_private` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'If TRUE, visible only to creator until/unless ownership rules override.',
  `rounding_strategy` enum('NONE','ROUND_HALF_UP','TRIM_TO_UNIT') NOT NULL DEFAULT 'ROUND_HALF_UP' COMMENT 'How to round per-participant shares.',
  `tail_allocation` enum('PAYER','LARGEST_SHARE','CREATOR') NOT NULL DEFAULT 'PAYER' COMMENT 'Who absorbs the rounding remainder.',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_txn_category` (`category_id`),
  KEY `fk_txn_payer` (`payer_id`),
  KEY `fk_txn_currency` (`currency`),
  KEY `idx_txn_ledger_time` (`ledger_id`,`txn_at`),
  KEY `idx_txn_type` (`ledger_id`,`type`,`txn_at`),
  KEY `idx_txn_ledger_category_time` (`ledger_id`,`category_id`,`txn_at`),
  KEY `idx_txn_creator_time` (`created_by`,`txn_at`),
  CONSTRAINT `fk_txn_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`),
  CONSTRAINT `fk_txn_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_txn_currency` FOREIGN KEY (`currency`) REFERENCES `currency` (`code`),
  CONSTRAINT `fk_txn_ledger` FOREIGN KEY (`ledger_id`) REFERENCES `ledgers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_txn_payer` FOREIGN KEY (`payer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Transactions are the canonical records. Splits/participants and debt edges reference this.';

-- ----------------------------
-- Records of transactions
-- ----------------------------
BEGIN;
INSERT INTO `transactions` (`id`, `ledger_id`, `created_by`, `txn_at`, `type`, `category_id`, `payer_id`, `amount_total`, `currency`, `note`, `is_private`, `rounding_strategy`, `tail_allocation`, `created_at`, `updated_at`) VALUES (1, 6, 4, '2025-10-21 19:00:00', 'EXPENSE', NULL, 4, 90.00000000, 'USD', 'Group Dinner', 0, 'ROUND_HALF_UP', 'PAYER', '2025-10-21 19:33:17', '2025-10-21 19:33:17');
INSERT INTO `transactions` (`id`, `ledger_id`, `created_by`, `txn_at`, `type`, `category_id`, `payer_id`, `amount_total`, `currency`, `note`, `is_private`, `rounding_strategy`, `tail_allocation`, `created_at`, `updated_at`) VALUES (2, 6, 6, '2025-10-21 21:30:00', 'EXPENSE', NULL, 6, 60.00000000, 'USD', 'Movie Tickets', 0, 'ROUND_HALF_UP', 'PAYER', '2025-10-21 19:33:43', '2025-10-21 19:33:43');
COMMIT;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL COMMENT 'Displayable user name; keep it simple.',
  `email` varchar(255) DEFAULT NULL COMMENT 'Optional login identifier.',
  `phone` varchar(32) DEFAULT NULL COMMENT 'Optional login identifier. E.164 if used.',
  `password_hash` varchar(255) NOT NULL COMMENT 'Hashed secret; exact hashing handled in app.',
  `timezone` varchar(64) NOT NULL DEFAULT 'America/New_York' COMMENT 'IANA timezone.',
  `main_currency` char(3) NOT NULL DEFAULT 'USD' COMMENT 'User’s preferred currency display.',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  KEY `fk_users_currency` (`main_currency`),
  CONSTRAINT `fk_users_currency` FOREIGN KEY (`main_currency`) REFERENCES `currency` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App users. Keep fields minimal by design.';

-- ----------------------------
-- Records of users
-- ----------------------------
BEGIN;
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (1, 'testU', 'hzh@gmail.com', NULL, 'hcMMBnMnX+fVGm4RPEu5XA==@qRGbEmcUKQN6yTeOmFVOxaaz6gOW16R8+XbD3duNFnA=', 'America/New_York', 'USD', '2025-10-16 23:17:38', '2025-10-16 23:17:38');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (2, 'testU', 'hzh2@gmail.com', NULL, 'rFkyf3q6+mHOegRb1aCOtQ==@e3c2gpqV36aE/Okwh5pyeqIVvRMqlxEPsix9eUTi3Nc=', 'America/New_York', 'USD', '2025-10-16 23:21:21', '2025-10-16 23:21:21');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (3, 'aaa', 'aaa@gmail.com', NULL, 'YSqXRWhUAcp9kiiBO/3Tlg== @kw6hk/aV0Ywh9bjDSPFvtIOoCQYapM5i+xEREThjYas=', 'America/New_York', 'USD', '2025-10-21 02:07:06', '2025-10-21 02:07:06');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (4, 'Alice Anderson', 'alice.anderson@example.org', NULL, '6s2hZK940nRb0FbaQorjqA== @BNvkXevmn5c3bog/YrMUQwEBQ4y8KWEWfHTYSvgS1tI=', 'America/New_York', 'USD', '2025-10-21 19:12:38', '2025-10-21 19:12:38');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (5, 'Charlie Clark', 'charlie.clark@example.org', NULL, 'fPWVKPty5dUJ+RQE2rx5GA== @QDTZCpdu7n31RqiOQ4MOZ3wXHlJ0t6do8S4BYIvTTjo=', 'America/New_York', 'USD', '2025-10-21 19:12:38', '2025-10-21 19:12:38');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (6, 'user_b3ef93e0403f', 'bob.baker@example.org', NULL, 'r8410wcUeOt3ieUMQTBk/w== @ZK8JW4SRf1QsL6SNvtctn2lMxRqmz/airUoeOyI09aQ=', 'America/New_York', 'USD', '2025-10-21 19:17:17', '2025-10-21 19:17:17');
INSERT INTO `users` (`id`, `name`, `email`, `phone`, `password_hash`, `timezone`, `main_currency`, `created_at`, `updated_at`) VALUES (7, 'my', 'my@gmail.com', NULL, 'CJV6/lLKy75H4P4jCOCtaA== @gq9bnudB25gIMbWj08gZYGOL3+HbkaxwddpfSCS9e2o=', 'America/New_York', 'USD', '2025-10-21 19:28:12', '2025-10-21 19:28:12');
COMMIT;

SET FOREIGN_KEY_CHECKS = 1;
