-- Test data seed for Integration Tests
-- This file should be run after ledger_flow.sql to populate initial test data

USE ledger;

-- Insert additional currencies (if needed)
INSERT INTO currency (code, exponent) VALUES
('EUR', 2),
('GBP', 2),
('JPY', 0),
('CNY', 2)
ON DUPLICATE KEY UPDATE exponent = VALUES(exponent);

-- Note: Categories are ledger-specific and will be created in each integration test
-- as they require a valid ledger_id

