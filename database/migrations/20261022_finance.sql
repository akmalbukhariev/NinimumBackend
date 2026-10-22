-- TZ v10 §9: money places, expenses, transfers, manual income and cash counts (owner and Director only). Safe to run more than once.
-- Needs 20261021_strict_rules.sql (Q-18 row).
CREATE TABLE IF NOT EXISTS finance_accounts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(20) NOT NULL,
 name VARCHAR(100) NOT NULL,
 -- With an opening date, the balance starts from opening_balance on that day; without one, from zero at the start.
 opening_balance DECIMAL(16,2) NOT NULL DEFAULT 0,
 opening_date DATE NULL,
 updated_by VARCHAR(160) NULL,
 updated_at DATETIME NULL,
 UNIQUE KEY uk_finance_accounts_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO finance_accounts(code,name) VALUES ('CASH','Naqd kassa'),('BANK','Bank hisobi'),('ONLINE','Onlayn to‘lov (Payme)');

CREATE TABLE IF NOT EXISTS expense_types (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 is_active TINYINT(1) NOT NULL DEFAULT 1,
 UNIQUE KEY uk_expense_types_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO expense_types(name) VALUES ('Ijara'),('Oylik'),('Kuryer'),('Reklama'),('Kommunal'),('Bank xizmati'),('Boshqa');

-- kind: EXPENSE (from account_id), INCOME (into account_id), TRANSFER (account_id → to_account_id). Never deleted: REVERSED instead.
CREATE TABLE IF NOT EXISTS finance_documents (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 kind VARCHAR(20) NOT NULL,
 doc_date DATE NOT NULL,
 amount DECIMAL(16,2) NOT NULL,
 account_id BIGINT NOT NULL,
 to_account_id BIGINT NULL,
 expense_type_id BIGINT NULL,
 counterparty VARCHAR(200) NULL,
 note VARCHAR(500) NULL,
 receipt_photo MEDIUMBLOB NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 reversed_by VARCHAR(160) NULL,
 reversed_at DATETIME NULL,
 reversal_reason VARCHAR(500) NULL,
 INDEX ix_finance_documents_date(doc_date),
 INDEX ix_finance_documents_account(account_id,doc_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cash_counts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 count_date DATE NOT NULL,
 counted_amount DECIMAL(16,2) NOT NULL,
 system_amount DECIMAL(16,2) NOT NULL,
 note VARCHAR(500) NULL,
 created_by VARCHAR(160) NOT NULL,
 created_at DATETIME NOT NULL,
 INDEX ix_cash_counts_account(account_id,count_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A supplier payment can say which money place it was paid from; older records stay outside the balances.
ALTER TABLE supplier_payment_records ADD COLUMN IF NOT EXISTS account_id BIGINT NULL;

INSERT IGNORE INTO strict_rules(code,staff_enabled,owner_enabled) VALUES ('Q-18',1,0);
