-- Manual test fixture set. Loaded on every startup (in-memory H2, so it's the only source of data).
-- Covers the three cases the retirement-age rule needs: over 65 + RETIREMENT, under 65 + RETIREMENT, SAVINGS only.

INSERT INTO investors (id, first_name, last_name, email, date_of_birth) VALUES
    (1, 'Thandiwe', 'Nkosi', 'thandiwe.nkosi@example.com', '1955-03-14'),
    (2, 'Sipho', 'Mahlangu', 'sipho.mahlangu@example.com', '1980-06-01'),
    (3, 'Lerato', 'Dube', 'lerato.dube@example.com', '1990-11-23');

-- Investor 1: age 71 (2026-08-19), RETIREMENT withdrawals allowed
INSERT INTO products (id, name, type, balance, investor_id) VALUES
    (1, 'Retirement Annuity', 'RETIREMENT', 850000.00, 1);

-- Investor 2: age 46, holds a RETIREMENT product but is too young to withdraw from it
INSERT INTO products (id, name, type, balance, investor_id) VALUES
    (2, 'Retirement Annuity', 'RETIREMENT', 320000.00, 2);

-- Investor 3: age 35, SAVINGS only - no age restriction applies to this investor
INSERT INTO products (id, name, type, balance, investor_id) VALUES
    (3, 'Flexible Savings Account', 'SAVINGS', 45000.00, 3),
    (4, 'Tax-Free Savings Account', 'SAVINGS', 12500.50, 3);
