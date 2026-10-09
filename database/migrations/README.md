# Order delivery address update

Apply `20261009_user_addresses.sql` and then `20261008_order_delivery_address.sql` to the target database before deploying this backend. The first script creates the address table if missing; the second adds order columns and must only run once. If you already applied the order-column update, run only `20261009_user_addresses.sql`. Deploy the backend before releasing the updated customer app.

New orders store their selected address and coordinates independently of the customer profile. The backend creates an address record owned by the ordering customer; it no longer trusts an address ID supplied by a client. Older clients without the new fields use a validated copy of their current profile address. Incomplete or outside-Shahrisabz addresses are rejected.

Historical orders retain the existing profile fallback because their original delivery coordinates are unknown. This update does not repair those orders automatically.

Acceptance check: place three orders at different Shahrisabz locations. Verify each selected address in admin and each destination in the assigned courier's map. Change the customer's profile address and confirm those new orders still retain their original destinations.

The SQL migration has been prepared locally; it is not automatically applied by the application.

## Ready stage

For the separate `READY` order stage, run `20261009_order_ready_status.sql` before deploying the backend. This script expects the existing warehouse preparation tables. It expands the status field and updates existing checked warehouse orders to `READY`, without changing cancelled, dispatched, or delivered orders. No new address migration is needed if those scripts were already applied.

Deploy the updated backend and admin panel, and install the updated customer and warehouse apps. The courier app does not need a new build for this stage.

Check one new order through these actions:

1. Payment completes: `CONFIRMED`.
2. Warehouse worker begins preparation: `PREPARING`.
3. All items are checked and the worker marks the order ready: `READY`.
4. Courier accepts the order: it remains `READY`.
5. Courier starts the route: `ON_THE_WAY`.
6. Courier completes delivery: `DELIVERED`.

Refresh the admin and customer screens at each stage. An incomplete warehouse order cannot be marked ready. Existing cancellation protections remain active. Full status audit history and owner override are separate remaining K3 work.

## Legacy address foreign key repair

If `SHOW CREATE TABLE orders` shows `fk_orders_address` referencing `addresses(id)`, apply `20261009_order_address_foreign_key.sql` once. The `user_addresses` table must already exist. This preserves historical address links as `legacy_address_id`, including their existing foreign key, and creates a new nullable `address_id` referencing `user_addresses(address_id)`. Existing order snapshots remain unchanged; historical orders have a null new address link. The current backend reads delivery snapshots and profile fallback, so no backend or app rebuild is required for this repair. Do not run this script again after it succeeds.

## Order status history

Run `20261009_order_status_history.sql` once before deploying this backend. It adds transactional status audit triggers and an indexed history table. Each existing order receives an explicitly labelled baseline at migration time; earlier changes are not reconstructed. Every subsequent creation and actual status change is recorded in the same transaction as the order write. Refreshes and updates that keep the same status do not create duplicate entries. History survives customer order-history hiding and unpaid-order deletion.

Deploy the backend and admin panel. No mobile app update is required for this step. Open Orders → Details → Status history to view the most recent 500 records: UTC timestamp displayed in the browser's timezone, previous/new status, authenticated actor (or customer/courier ID for system reconciliation), source and reason where supplied. Manual database changes without attribution are marked SYSTEM, never assigned to the previous actor. The history table is read-only through the API.

Check a new paid order through warehouse preparation, readiness, courier acceptance, route start and delivery. History must contain the actual status transitions; acceptance leaves READY unchanged and creates no duplicate status event. A cancelled order must show the administrator/customer and cancellation reason. Refresh the details and confirm that records do not multiply.

Owner override and return handling are separate remaining work; this history update does not add arbitrary status editing.

## Owner status correction

After status history has been installed, deploy the updated backend and admin panel. No further SQL or mobile app build is required. Only an active authenticated SUPER_ADMIN can use Orders → Details → Correct status. The server independently checks owner authority, a required 1–500 character reason, the expected current status, and a strictly earlier operational stage. Cancelled or unpaid/refunded orders cannot be reopened. Payment and stock are not changed by a status correction; returns/refunds remain separate actions.

A return to On the way requires a previously checked/packed order and an active assigned courier; it clears completion timestamps. Returning to Ready clears the courier assignment for a fresh acceptance. Returning to Preparing clears the assignment and product checks while retaining an active warehouse worker. Returning to Confirmed clears preparation items and worker assignment so the order can be claimed again. All writes and the owner/history entries share one transaction. Old history remains visible, including the reason and owner login.

Acceptance: use a delivered test order with an active courier. Correct Delivered → On the way with a reason, verify the owner/history event and courier active order, then complete it again through the courier app. Ordinary ADMIN accounts must not have the action and their direct requests must be rejected. A blank reason, a stale current status, and a correction to an unverified ready stage must fail without changing order, preparation, delivery or audit records.


## Failed-delivery return status (2026-10-09)
Apply `20261009_order_returning.sql` after the READY and status-history migrations,
then deploy the backend package, admin `dist`, and rebuild the courier and customer apps.
The repair updates only FAILED jobs whose orders still say READY/ON_THE_WAY, logging
RETURN_REPAIR at the actual repair time. It preserves payment, stock and prior history.
New courier failure requires a reason (1–500 characters), moves the canonical order
into RETURNING and logs the courier identity and reason atomically. The internal job
keeps FAILED for compatibility with existing clients/database enums. Returning orders
stay in the courier active list but are excluded from delivery route directions and
cannot be completed or reassigned through ordinary transitions. Admin failure also
requires a reason. Paid RETURNING orders display Refund pending. Opening an older
failed job can repair a missed status as well. Customer order list/progress says Returning.

Verify order 72 after repair, then a fresh order: prepare -> ready -> courier route ->
cannot deliver -> choose reason. Panel and customer should show Returning, audit should
show ON_THE_WAY -> RETURNING with the authenticated courier and reason, and payment
should remain PAID until Payme confirms a refund. Refresh must not add duplicate history.
This change covers failure status and reason recording. Warehouse receipt/restocking,
photos, partial returns and return notifications are separate remaining work.
