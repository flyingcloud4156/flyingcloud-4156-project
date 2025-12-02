-- ======================================================================================
-- FILE: ops/sql/backup/ledger_big_seed.sql
-- PURPOSE:
--   - Populate ledger DB with a lot of realistic demo data
--   - 2 ledgers, 7 users, categories, ~20+ transactions, splits, debt_edges
-- NOTES:
--   - Does NOT drop database, only truncates data tables
-- ======================================================================================

USE ledger;

SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE settlements;
TRUNCATE TABLE attachments;
TRUNCATE TABLE budgets;
TRUNCATE TABLE import_jobs;
TRUNCATE TABLE ledger_user_balances;
TRUNCATE TABLE debt_edges;
TRUNCATE TABLE transaction_splits;
TRUNCATE TABLE transactions;
TRUNCATE TABLE categories;
TRUNCATE TABLE ledger_members;
TRUNCATE TABLE ledgers;
TRUNCATE TABLE users;
SET FOREIGN_KEY_CHECKS=1;

-- --------------------------
-- Users (password is Passw0rd!)
-- --------------------------
INSERT INTO users
(id, name, email, phone, password_hash, timezone, main_currency, created_at, updated_at)
VALUES
(1, 'Alice',   'alice@gmail.com',   NULL,
'm0O0fduHkulIcvl4T3DIfw== @Fq/ZhtfI69n54WC1MkxnSlcTV3lzZsoHNiudM59/EbQ=',
'America/New_York', 'USD', NOW(), NOW()),

(2, 'Bob',     'bob@gmail.com',     NULL,
'UMnvzuB4+IzvIzlKpLXWlQ== @jYseZX8ePqdP7HeghV/6X3UPdOzsmJvzmBAb2ES/coA=',
'America/New_York', 'USD', NOW(), NOW()),

(3, 'Charlie', 'charlie@gmail.com', NULL,
'DNEuf4TJPPlBOzOOIpVTeA== @3qY2xJ1kh09TzAr2Ej+wYbVtIeUYzr24RMhze8B6CG4=',
'America/New_York', 'USD', NOW(), NOW()),

(4, 'Diana',   'diana@gmail.com',   NULL,
'ANUU2BIJT1JdEkVdOp7tCA== @H3oUEV4PVPwcG111YfjTGhUpA925VMOjB5jkArXOkCw=',
'America/New_York', 'USD', NOW(), NOW()),

(5, 'Evan',    'evan@gmail.com',    NULL,
'yAAsd2YTf8w+r/6Sazkvzw== @YdB18Cf9GzU0RBf3U2P8NOocgJ9YKP6mF5cAEglxNPY=',
'America/New_York', 'USD', NOW(), NOW()),

(6, 'Fay',     'fay@gmail.com',     NULL,
'bF8cIoKCMKfLjBYZcP8UYQ== @qqY0ILBb/K04k51wRMUkWC3qGtCdZH6DA9an7N49agw=',
'America/New_York', 'USD', NOW(), NOW()),

(7, 'Gina',    'gina@gmail.com',    NULL,
'o+u7sSO4tl/4zxWS2xw3LQ== @EMUdHrunvxzJGp90uHQejdPMjLzjY2yi+cYrurlpe4U=',
'America/New_York', 'USD', NOW(), NOW());

-- --------------------------
-- Ledgers
-- --------------------------
INSERT INTO ledgers (id, name, owner_id, ledger_type, base_currency, share_start_date, created_at, updated_at) VALUES
(1,'Road Trip Demo',1,'GROUP_BALANCE','USD','2025-01-01',NOW(),NOW()),
(2,'Apartment Demo',4,'GROUP_BALANCE','USD','2025-01-01',NOW(),NOW());

-- Ledger 1 members: 1,2,3,4
INSERT INTO ledger_members (ledger_id, user_id, role, joined_at) VALUES
(1,1,'OWNER',NOW()),
(1,2,'EDITOR',NOW()),
(1,3,'EDITOR',NOW()),
(1,4,'EDITOR',NOW());

-- Ledger 2 members: 4,5,6,7
INSERT INTO ledger_members (ledger_id, user_id, role, joined_at) VALUES
(2,4,'OWNER',NOW()),
(2,5,'EDITOR',NOW()),
(2,6,'EDITOR',NOW()),
(2,7,'EDITOR',NOW());

-- --------------------------
-- Categories
-- --------------------------
INSERT INTO categories (id, ledger_id, name, kind, is_active, sort_order) VALUES
(1,1,'Gas','EXPENSE',TRUE,10),
(2,1,'Food','EXPENSE',TRUE,20),
(3,1,'Lodging','EXPENSE',TRUE,30),
(4,1,'Entertainment','EXPENSE',TRUE,40),
(5,1,'Supplies','EXPENSE',TRUE,50),

(6,2,'Rent','EXPENSE',TRUE,10),
(7,2,'Groceries','EXPENSE',TRUE,20),
(8,2,'Utilities','EXPENSE',TRUE,30),
(9,2,'Internet','EXPENSE',TRUE,40),
(10,2,'Dining','EXPENSE',TRUE,50);

-- ======================================================================================
-- Ledger 1: Road Trip Demo
-- transactions id: 1..10
-- group: users 1,2,3,4; equal splits; payer varies
-- ======================================================================================

INSERT INTO transactions
(id, ledger_id, created_by, txn_at, type, category_id, payer_id, amount_total, currency, note, is_private)
VALUES
(1, 1, 1, '2025-09-03 10:00:00', 'EXPENSE', 1, 1, 120.00, 'USD', 'Gas - Sep 3', 0),
(2, 1, 2, '2025-09-06 12:30:00', 'EXPENSE', 2, 2, 200.00, 'USD', 'Food - Sep 6', 0),
(3, 1, 3, '2025-09-10 19:00:00', 'EXPENSE', 3, 3, 320.00, 'USD', 'Lodging - Sep 10', 0),
(4, 1, 4, '2025-09-15 21:00:00', 'EXPENSE', 4, 4, 80.00,  'USD', 'Movie night - Sep 15', 0),

(5, 1, 1, '2025-10-03 10:00:00', 'EXPENSE', 1, 1, 140.00, 'USD', 'Gas - Oct 3', 0),
(6, 1, 2, '2025-10-08 18:00:00', 'EXPENSE', 2, 2, 180.00, 'USD', 'Food - Oct 8', 0),
(7, 1, 3, '2025-10-15 20:00:00', 'EXPENSE', 4, 3, 120.00, 'USD', 'KTV - Oct 15', 0),

(8, 1, 4, '2025-11-02 09:30:00', 'EXPENSE', 5, 4, 160.00, 'USD', 'Supplies - Nov 2', 0),
(9, 1, 1, '2025-11-10 19:30:00', 'EXPENSE', 2, 1, 220.00, 'USD', 'Food - Nov 10', 0),
(10,1, 2, '2025-11-18 11:00:00', 'EXPENSE', 1, 2, 100.00, 'USD', 'Gas - Nov 18', 0);

-- Each transaction is split equally among 4 people
INSERT INTO transaction_splits (transaction_id, user_id, split_method, share_value, included, computed_amount) VALUES
(1,1,'EXACT',30.00,1,30.00),(1,2,'EXACT',30.00,1,30.00),(1,3,'EXACT',30.00,1,30.00),(1,4,'EXACT',30.00,1,30.00),
(2,1,'EXACT',50.00,1,50.00),(2,2,'EXACT',50.00,1,50.00),(2,3,'EXACT',50.00,1,50.00),(2,4,'EXACT',50.00,1,50.00),
(3,1,'EXACT',80.00,1,80.00),(3,2,'EXACT',80.00,1,80.00),(3,3,'EXACT',80.00,1,80.00),(3,4,'EXACT',80.00,1,80.00),
(4,1,'EXACT',20.00,1,20.00),(4,2,'EXACT',20.00,1,20.00),(4,3,'EXACT',20.00,1,20.00),(4,4,'EXACT',20.00,1,20.00),
(5,1,'EXACT',35.00,1,35.00),(5,2,'EXACT',35.00,1,35.00),(5,3,'EXACT',35.00,1,35.00),(5,4,'EXACT',35.00,1,35.00),
(6,1,'EXACT',45.00,1,45.00),(6,2,'EXACT',45.00,1,45.00),(6,3,'EXACT',45.00,1,45.00),(6,4,'EXACT',45.00,1,45.00),
(7,1,'EXACT',30.00,1,30.00),(7,2,'EXACT',30.00,1,30.00),(7,3,'EXACT',30.00,1,30.00),(7,4,'EXACT',30.00,1,30.00),
(8,1,'EXACT',40.00,1,40.00),(8,2,'EXACT',40.00,1,40.00),(8,3,'EXACT',40.00,1,40.00),(8,4,'EXACT',40.00,1,40.00),
(9,1,'EXACT',55.00,1,55.00),(9,2,'EXACT',55.00,1,55.00),(9,3,'EXACT',55.00,1,55.00),(9,4,'EXACT',55.00,1,55.00),
(10,1,'EXACT',25.00,1,25.00),(10,2,'EXACT',25.00,1,25.00),(10,3,'EXACT',25.00,1,25.00),(10,4,'EXACT',25.00,1,25.00);

-- Debt edges: payer 是 creditor，其余人欠 payer 各自份额
INSERT INTO debt_edges
(id, ledger_id, transaction_id, from_user_id, to_user_id, amount, edge_currency, created_at) VALUES
-- txn 1, payer=1
(1,1,1,1,2,30.00,'USD','2025-09-03 10:00:00'),
(2,1,1,1,3,30.00,'USD','2025-09-03 10:00:00'),
(3,1,1,1,4,30.00,'USD','2025-09-03 10:00:00'),
-- txn 2, payer=2
(4,1,2,2,1,50.00,'USD','2025-09-06 12:30:00'),
(5,1,2,2,3,50.00,'USD','2025-09-06 12:30:00'),
(6,1,2,2,4,50.00,'USD','2025-09-06 12:30:00'),
-- txn 3, payer=3
(7,1,3,3,1,80.00,'USD','2025-09-10 19:00:00'),
(8,1,3,3,2,80.00,'USD','2025-09-10 19:00:00'),
(9,1,3,3,4,80.00,'USD','2025-09-10 19:00:00'),
-- txn 4, payer=4
(10,1,4,4,1,20.00,'USD','2025-09-15 21:00:00'),
(11,1,4,4,2,20.00,'USD','2025-09-15 21:00:00'),
(12,1,4,4,3,20.00,'USD','2025-09-15 21:00:00'),
-- txn 5, payer=1
(13,1,5,1,2,35.00,'USD','2025-10-03 10:00:00'),
(14,1,5,1,3,35.00,'USD','2025-10-03 10:00:00'),
(15,1,5,1,4,35.00,'USD','2025-10-03 10:00:00'),
-- txn 6, payer=2
(16,1,6,2,1,45.00,'USD','2025-10-08 18:00:00'),
(17,1,6,2,3,45.00,'USD','2025-10-08 18:00:00'),
(18,1,6,2,4,45.00,'USD','2025-10-08 18:00:00'),
-- txn 7, payer=3
(19,1,7,3,1,30.00,'USD','2025-10-15 20:00:00'),
(20,1,7,3,2,30.00,'USD','2025-10-15 20:00:00'),
(21,1,7,3,4,30.00,'USD','2025-10-15 20:00:00'),
-- txn 8, payer=4
(22,1,8,4,1,40.00,'USD','2025-11-02 09:30:00'),
(23,1,8,4,2,40.00,'USD','2025-11-02 09:30:00'),
(24,1,8,4,3,40.00,'USD','2025-11-02 09:30:00'),
-- txn 9, payer=1
(25,1,9,1,2,55.00,'USD','2025-11-10 19:30:00'),
(26,1,9,1,3,55.00,'USD','2025-11-10 19:30:00'),
(27,1,9,1,4,55.00,'USD','2025-11-10 19:30:00'),
-- txn 10, payer=2
(28,1,10,2,1,25.00,'USD','2025-11-18 11:00:00'),
(29,1,10,2,3,25.00,'USD','2025-11-18 11:00:00'),
(30,1,10,2,4,25.00,'USD','2025-11-18 11:00:00'),

-- A few extra manual AR/AP edges (without transaction_id) to make AR/AP more asymmetric
(31,1,NULL,2,3,25.00,'USD','2025-10-20 09:00:00'),
(32,1,NULL,3,2,10.00,'USD','2025-11-05 09:00:00');

-- ======================================================================================
-- Ledger 2: Apartment Demo
-- transactions id: 101..110
-- group: users 4,5,6,7; rent + utilities + groceries + dining
-- ======================================================================================

INSERT INTO transactions
(id, ledger_id, created_by, txn_at, type, category_id, payer_id, amount_total, currency, note, is_private)
VALUES
(101,2,4,'2025-09-01 09:00:00','EXPENSE',6,4,1800.00,'USD','Rent Sep',0),
(102,2,5,'2025-09-03 10:00:00','EXPENSE',8,5,210.00,'USD','Utilities Sep',0),
(103,2,6,'2025-09-05 19:00:00','EXPENSE',7,6,150.00,'USD','Groceries Sep #1',0),
(104,2,7,'2025-09-12 19:30:00','EXPENSE',10,7,120.00,'USD','Dining Sep',0),

(105,2,4,'2025-10-01 09:00:00','EXPENSE',6,4,1800.00,'USD','Rent Oct',0),
(106,2,5,'2025-10-03 10:00:00','EXPENSE',8,5,220.00,'USD','Utilities Oct',0),
(107,2,6,'2025-10-07 18:30:00','EXPENSE',7,6,165.00,'USD','Groceries Oct #1',0),
(108,2,7,'2025-10-15 20:00:00','EXPENSE',10,7,130.00,'USD','Dining Oct',0),

(109,2,4,'2025-11-01 09:00:00','EXPENSE',6,4,1800.00,'USD','Rent Nov',0),
(110,2,5,'2025-11-05 10:00:00','EXPENSE',7,5,180.00,'USD','Groceries Nov #1',0);

-- Split equally among users 4,5,6,7
INSERT INTO transaction_splits (transaction_id, user_id, split_method, share_value, included, computed_amount) VALUES
-- 101: 1800 / 4 = 450
(101,4,'EXACT',450.00,1,450.00),(101,5,'EXACT',450.00,1,450.00),(101,6,'EXACT',450.00,1,450.00),(101,7,'EXACT',450.00,1,450.00),
-- 102: 210 / 4 = 52.5
(102,4,'EXACT',52.50,1,52.50),(102,5,'EXACT',52.50,1,52.50),(102,6,'EXACT',52.50,1,52.50),(102,7,'EXACT',52.50,1,52.50),
-- 103: 150 / 4 = 37.5
(103,4,'EXACT',37.50,1,37.50),(103,5,'EXACT',37.50,1,37.50),(103,6,'EXACT',37.50,1,37.50),(103,7,'EXACT',37.50,1,37.50),
-- 104: 120 / 4 = 30
(104,4,'EXACT',30.00,1,30.00),(104,5,'EXACT',30.00,1,30.00),(104,6,'EXACT',30.00,1,30.00),(104,7,'EXACT',30.00,1,30.00),

-- 105: 1800 / 4 = 450
(105,4,'EXACT',450.00,1,450.00),(105,5,'EXACT',450.00,1,450.00),(105,6,'EXACT',450.00,1,450.00),(105,7,'EXACT',450.00,1,450.00),
-- 106: 220 / 4 = 55
(106,4,'EXACT',55.00,1,55.00),(106,5,'EXACT',55.00,1,55.00),(106,6,'EXACT',55.00,1,55.00),(106,7,'EXACT',55.00,1,55.00),
-- 107: 165 / 4 = 41.25
(107,4,'EXACT',41.25,1,41.25),(107,5,'EXACT',41.25,1,41.25),(107,6,'EXACT',41.25,1,41.25),(107,7,'EXACT',41.25,1,41.25),
-- 108: 130 / 4 = 32.5
(108,4,'EXACT',32.50,1,32.50),(108,5,'EXACT',32.50,1,32.50),(108,6,'EXACT',32.50,1,32.50),(108,7,'EXACT',32.50,1,32.50),

-- 109: 1800 / 4 = 450
(109,4,'EXACT',450.00,1,450.00),(109,5,'EXACT',450.00,1,450.00),(109,6,'EXACT',450.00,1,450.00),(109,7,'EXACT',450.00,1,450.00),
-- 110: 180 / 4 = 45
(110,4,'EXACT',45.00,1,45.00),(110,5,'EXACT',45.00,1,45.00),(110,6,'EXACT',45.00,1,45.00),(110,7,'EXACT',45.00,1,45.00);

-- Debt edges: payer is creditor, others owe payer
INSERT INTO debt_edges
(id, ledger_id, transaction_id, from_user_id, to_user_id, amount, edge_currency, created_at) VALUES
-- 101, payer=4
(201,2,101,4,5,450.00,'USD','2025-09-01 09:00:00'),
(202,2,101,4,6,450.00,'USD','2025-09-01 09:00:00'),
(203,2,101,4,7,450.00,'USD','2025-09-01 09:00:00'),
-- 102, payer=5
(204,2,102,5,4,52.50,'USD','2025-09-03 10:00:00'),
(205,2,102,5,6,52.50,'USD','2025-09-03 10:00:00'),
(206,2,102,5,7,52.50,'USD','2025-09-03 10:00:00'),
-- 103, payer=6
(207,2,103,6,4,37.50,'USD','2025-09-05 19:00:00'),
(208,2,103,6,5,37.50,'USD','2025-09-05 19:00:00'),
(209,2,103,6,7,37.50,'USD','2025-09-05 19:00:00'),
-- 104, payer=7
(210,2,104,7,4,30.00,'USD','2025-09-12 19:30:00'),
(211,2,104,7,5,30.00,'USD','2025-09-12 19:30:00'),
(212,2,104,7,6,30.00,'USD','2025-09-12 19:30:00'),

-- 105, payer=4
(213,2,105,4,5,450.00,'USD','2025-10-01 09:00:00'),
(214,2,105,4,6,450.00,'USD','2025-10-01 09:00:00'),
(215,2,105,4,7,450.00,'USD','2025-10-01 09:00:00'),
-- 106, payer=5
(216,2,106,5,4,55.00,'USD','2025-10-03 10:00:00'),
(217,2,106,5,6,55.00,'USD','2025-10-03 10:00:00'),
(218,2,106,5,7,55.00,'USD','2025-10-03 10:00:00'),
-- 107, payer=6
(219,2,107,6,4,41.25,'USD','2025-10-07 18:30:00'),
(220,2,107,6,5,41.25,'USD','2025-10-07 18:30:00'),
(221,2,107,6,7,41.25,'USD','2025-10-07 18:30:00'),
-- 108, payer=7
(222,2,108,7,4,32.50,'USD','2025-10-15 20:00:00'),
(223,2,108,7,5,32.50,'USD','2025-10-15 20:00:00'),
(224,2,108,7,6,32.50,'USD','2025-10-15 20:00:00'),

-- 109, payer=4
(225,2,109,4,5,450.00,'USD','2025-11-01 09:00:00'),
(226,2,109,4,6,450.00,'USD','2025-11-01 09:00:00'),
(227,2,109,4,7,450.00,'USD','2025-11-01 09:00:00'),
-- 110, payer=5
(228,2,110,5,4,45.00,'USD','2025-11-05 10:00:00'),
(229,2,110,5,6,45.00,'USD','2025-11-05 10:00:00'),
(230,2,110,5,7,45.00,'USD','2025-11-05 10:00:00'),


(231,2,NULL,5,6,18.00,'USD','2025-10-20 11:00:00');

-- END
