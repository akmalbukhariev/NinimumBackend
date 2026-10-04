# Warehouse stage one

This extends Ninimum Admin and the existing backend. The customer and delivery app source projects are unchanged. It is the foundation for a later warehouse phone application, not the whole v10 specification.

## Included

- Owner-only Warehouse page, in Uzbek, Russian and English.
- Warehouse location records with codes such as A-03, and shelf/receiving types.
- Stock receipt documents: create draft, review, confirm, reverse with a reason.
- Drafts leave stock unchanged. Confirmation increases the existing product stock once, even when retried.
- A confirmed receipt is retained. Reversal creates negative stock movements; it fails if available stock is insufficient. A repeated reversal has no further effect.
- History of existing opening stock, receipts, receipt reversals, Payme sales and Payme refund restorations, with product snapshot, quantity change, resulting stock, reference, actor and UTC timestamp.
- Read-only preparation queue: paid, non-cancelled/non-delivered orders without an accepted courier job; order products and barcodes can be viewed.
- Enabled warehouse stock can no longer be overwritten through the product editor/API. New products start with zero and gain stock through receipts. Product deletion deactivates the product to retain warehouse references.
- Payment callbacks lock the transaction row before changing stock. Concurrent/repeated callbacks cannot consume the same payment twice. Multi-product stock failures roll back the payment and its history.

## Boundaries of this stage

The product stock field still means **available to sell**, as in the existing apps. It is not a count of everything physically inside the warehouse. Successful payments still reduce it, and Payme refunds still restore it according to the existing behavior.

Locations on receipt lines record where incoming stock was placed. This release does not calculate current per-shelf quantities, physical/reserved balances, batches, expiry dates, picking tasks, barcode scanning, automatic courier assignment, supplier debts or financial reports. The preparation queue is for visibility; it does not prevent couriers from using the current delivery workflow. Do not treat a financial refund as evidence that delivered goods physically returned. Physical returns and reservations require the next stock-model migration.

Opening movements import current stock values. They do not certify a physical inventory count and do not reconstruct old sales/refunds. Stock updates executed directly in SQL, outside the application, are not audited by this stage.

## Activate on a test environment first

1. Back up the database and briefly stop the backend so stock cannot change during opening-balance import.
2. Execute `warehouse-step1.sql` against that environment's Ninimum database. It creates the new tables without deleting or resetting existing products/orders. Re-running it does not duplicate opening movements.
3. Set `warehouse.enabled: true` in the backend's active configuration, or set the backend process environment variable `WAREHOUSE_ENABLED=true`. The shipped setting defaults to false. If using systemd, the environment variable belongs in that service's environment, not only in your interactive terminal.
4. Build/deploy the backend and restart it. With warehouse enabled, startup validates the schema version. `/ninimum/api/v1/admin/warehouse/status` with an admin token must return `resultCode=100` and `enabled=true`.
5. Build/deploy NinimumAdmin and sign in as the owner (`SUPER_ADMIN`). The Warehouse menu appears. Restricted administrators can read only the feature-status endpoint and retain their existing operational access.
6. Complete the checks below before activating on production. Keep warehouse enabled once it is in use: disabling it restores the legacy editing behavior and stops recording stock history, creating gaps.

This work does not deploy or alter the live database automatically.

## Quick user check

1. Open Warehouse → Locations and create A-03.
2. Open Receipts and create a draft for 3 units of an existing active product. Select A-03.
3. Check stock: the quantity must be unchanged while the document is a draft.
4. Open that receipt and press Confirm and add stock. Stock must increase by 3, with a RECEIPT entry in history.
5. Refresh/reopen the receipt. There is no second stock increase.
6. Buy 1 unit through the customer app using a test Payme payment. Stock decreases by 1 and history shows SALE. The paid order appears in the preparation queue until accepted by a courier.
7. Test a refund using a separate test order; the existing refund restoration appears as PAYMENT_REFUND only once.
8. Reverse a receipt with a reason. The original remains visible, marked reversed, and stock decreases by its quantities. Insufficient stock must reject reversal without changing any line.
9. Check Uzbek, Russian and English, and verify a restricted administrator cannot open `/warehouse` or its data/write APIs.

## Backend automated checks

The usual backend tests cover role access and existing behavior. `WarehouseIntegrationTest` is opt-in and requires a **disposable**, isolated MariaDB database named `warehouse_test`, bound to loopback. Its fixtures drop/recreate tables in that database. Never point it at real data.

Example test argument: `-Dwarehouse.test.jdbc=jdbc:mariadb://127.0.0.1:33317/warehouse_test`.

The database tests cover receipt retries/concurrent confirmation, reversal constraints, multi-line rollback, idempotent migration, paid-queue filtering, real Payme mapper execution, callback retries/concurrency, refunds and insufficient stock rollback.
