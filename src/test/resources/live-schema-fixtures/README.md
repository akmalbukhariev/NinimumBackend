# Local integration-test schema fixtures

These table definitions were recovered through read-only inspection of the deployed Ninimum schema on 17 October 2026. They replace missing file references in old integration tests. They contain no production rows, user credentials, payment tokens, or copied balances.

These are **test fixtures, not production migration scripts**. Do not deploy them. Warehouse opening entries are synthetic rows derived from each test's own products; timer settings use schema defaults. AUTO_INCREMENT counters are intentionally omitted. Current migrations (receipt approvals, locations, receipt metadata and complaints) are applied separately by tests.

Tests remain guarded to dedicated loopback databases (`warehouse_test` and `timers_test`). Fixture changes do not authorize live writes or payment execution. The schema snapshot and read-only verification evidence are recorded in the chat project's audit directory.
