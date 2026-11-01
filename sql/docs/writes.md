# SQL writes and transactions

Start with the [SQL overview](../README.md) for installation and a first query.

## Choosing a SQL write API

| API | Planning and visibility | Completion and recovery |
| --- | --- | --- |
| `execute` / `prepareMutation(...).execute` | Reads and writes one statement within serializable autocommit; joins an owning scope when present | Durable results after autocommit; resolve the retained transaction on an uncertain commit |
| `transaction` / `beginTransaction` / `session` with BEGIN | Ordered reads and writes see staged changes; protects predicates, including empty selections | Provisional results until the outer commit; savepoints and rollback before submission |
| `prepareAtomic(...).execute` / `executeAtomic` | Selects fixed targets before submission; statements do not see each other's writes | Standalone all-or-nothing commit; retain and retry the same prepared change set |

Use ordinary execution for predicate writes and upsert, a transaction for several
dependent statements, and a prepared atomic change set for a fixed batch whose
keys and guards must remain identical across retries. The two batch contracts
share storage machinery but have different read guarantees. Check
`capabilities.serializableTransactions` for autocommit and scopes, and
`supportsAtomicWrites` / `atomicWriteCapabilities` for prepared atomic batches.
These last two properties are extensions from `maryk.sql`.

## Mutation statements

`MarykSql.execute(...)` supports bounded INSERT VALUES and INSERT
SELECT, UPDATE (including FROM), soft DELETE (including USING), UNDELETE and DELETE HARD. Each statement is planned,
staged and committed as one serializable autocommit transaction. Predicates may
use the supported SQL expressions and subqueries; WHERE remains mandatory for
updates and deletes. Selected records carry version guards, and the transaction
also protects predicate reads against other writers. Hard deletion includes
soft-deleted records. Remote authorization still sees a DeleteRequest.

Writes support scalar properties, including embedded paths. UPDATE SET field = NULL removes an
optional stored property; INSERT VALUES(NULL) leaves it absent. Required fields
reject NULL. Omitted insert fields remain absent rather than applying model
defaults. Duplicate assignments and non-scalar properties are rejected. Values
must match the SQL column type with lossless exact numeric conversion; use CAST
explicitly to change types or round to stored precision.

```kotlin
import maryk.core.query.responses.AtomicStatus

val changed = sql.execute(
    "UPDATE Person SET nickname = NULL WHERE __key = ? AND __version = ? RETURNING __key",
    listOf(personKey, personVersion),
)
check(changed.response.status == AtomicStatus.Committed)
```

`SqlAtomicWriteResult.affectedRows` counts confirmed mutation targets, and
`response` supplies the structured outcome. A missing key or a version predicate
that no longer matches yields zero rows. Multi-row inserts cannot partly succeed.
RETURNING rows contain durable versions after autocommit. Interactive scopes,
savepoints and the connection-style control statements are described below.
`INSERT … ON CONFLICT` is supported through serializable execution; joined UPDATE … FROM and DELETE … USING are supported through the same serializable execution.

## SQL transactions

`sql.transaction { tx -> ... }` combines reads and writes in a serializable scope.
Queries and subsequent writes see earlier staged results, including INSERT SELECT,
predicate UPDATE/DELETE, UNDELETE and DELETE HARD. `WHERE` remains mandatory on
updates and deletes. RETURNING supports the same scalar projections and aliases as
prepared writes. Its staged versions are provisional until commit.

```kotlin
import maryk.sql.transaction
import maryk.core.query.responses.TransactionStatus

val result = sql.transaction { tx ->
    tx.execute("INSERT INTO Person (name) VALUES ('Ada')")
    val point = tx.savepoint()
    tx.execute("UPDATE Person SET name = 'Lin' WHERE name = 'Ada'")
    tx.rollbackTo(point)
    tx.query("SELECT name FROM Person WHERE name = 'Ada'")
}
if (result.commit.status == TransactionStatus.Committed) {
    println(result.value)
}
```

`sql.beginTransaction()` exposes manual commit, rollback and savepoint ownership.
Both entry points accept `readOnly = true`. `tx.query(..., readOptions = ...)`
also retains historical and include-deleted options when the backend supports them. `sql.capabilities.serializableTransactions`
reports support. Query consistency inside the transaction follows the shared
revision fence; outside a transaction, Current query consistency is unchanged.

`sql.session()` supplies sequential connection-style execution. It accepts BEGIN,
BEGIN TRANSACTION, START TRANSACTION, COMMIT, ROLLBACK, SAVEPOINT name,
ROLLBACK TO [SAVEPOINT] name and RELEASE [SAVEPOINT] name. BEGIN/START also accept
READ ONLY or READ WRITE. Queries and mutations join the active session; writes
outside a session transaction use autocommit. Closing a session rolls back work
that has not submitted commit. An uncertain submitted commit remains available
through `session.activeTransaction`; repeat COMMIT or resolve that transaction.
Control statements use the same bounded lexer, including comments and quoted names.
Queries, statements and control operations serialize through the same scope lock.
Read-only scopes reject every mutation statement, including predicates matching
zero rows. FoundationDB currently reports no authoritative SQL history watermark;
its current transaction view remains serializable, while SQL snapshot and AtVersion
reads fail explicitly.

Ordinary `sql.execute` plans and stages one statement before committing it. It
returns confirmed affected rows and RETURNING values with durable versions after
commit. A duplicate, validation failure or conflict publishes no partial statement.
For OutcomeUnknown, `result.transaction` retains the exact commit for
`resolveCommit()`. Cancellation after submission exposes the handle through
`TransactionCancellationException`. Never rerun a fresh statement to resolve an
uncertain commit. The shared 30-second lifetime, 2,000 journal requests, 1,000
mutations, 64 savepoints, 100,000 read keys/rows and backend payload/work budgets apply alongside SQL's
execution limits. There are no weaker isolation settings or silent batch splits.

See the [datastore transaction contract](../../core/docs/query.md#serializable-transactions)
for provisional versions, explicit retries, receipt retention and conservative
conflicts with unrelated writes.

## Atomic SQL change sets

Use `prepareAtomic` to plan a bounded change set and `execute()` on the prepared object to commit it. The backend must support Maryk atomic requests; `sql.supportsAtomicWrites` exposes this capability. Ordinary `MarykSql.execute(...)` uses a serializable autocommit transaction, supports general bounded predicates and RETURNING, and returns `SqlAtomicWriteResult`.

```kotlin
import maryk.sql.SqlWriteStatement
import maryk.core.query.responses.AtomicStatus

val prepared = sql.prepareAtomic(
    changeSetId = "import-2026-10-10-001",
    statements = listOf(
        SqlWriteStatement(
            "INSERT INTO Person (name, age) VALUES ('Ada', 37), ('Lin', 42) " +
                "RETURNING __key, __version, name",
        ),
        SqlWriteStatement(
            "UPDATE Invoice SET status = 'reviewed' WHERE status = 'draft' RETURNING __key",
        ),
    ),
)
val result = prepared.execute()
if (result.response.status == AtomicStatus.Committed) {
    println(result.affectedRows)
    result.returning.forEach { statementResult ->
        statementResult.rows.forEach { row -> println(row.values) }
    }
}
```

A single statement can use `prepareAtomic(id, statement, parameters)` or `executeAtomic(id, statement, parameters)`. `executeAtomic` plans and executes once. Retain the prepared object and call its `execute()` again after a lost connection, cancellation, timeout, or `OutcomeUnknown`; replanning can choose different keys, records, values, or guards. The prepared object's `request` exposes the normalized Maryk change set. Successful receipts replay the original operation responses without rerunning stale guards. Retain the prepared object when the caller needs the same `RETURNING` result; the serialized Maryk request alone does not contain those row projections.

Atomic SQL supports:

- Multi-row `INSERT … VALUES` and `INSERT … SELECT`, including bounded query expressions, joins, and CTEs supported by the read engine. Insert queries must produce the same number of columns as the target list.
- Multiple independently parameterized statements across registered models in one change set.
- `UPDATE`, soft `DELETE`, and `UNDELETE` with general SQL `WHERE` predicates. `WHERE` remains mandatory. Table scans still require `SqlOptions(allowTableScan = true)`.
- `RETURNING` scalar expressions, `*`, qualified columns/wildcards, and optional aliases. Aggregates, window functions and subqueries are unsupported in RETURNING. The durable `__version` can only be returned as a direct column; it cannot appear inside a computed returning expression. Updates return assigned values, inserts return planned stored values without filling omitted defaults, and deletes return the selected values before deletion. `__version` is the version reported by the committed member, including an unchanged version for a no-op.

All selected mutation targets carry their observed version into the commit. A stale target, duplicate key, unique conflict, or model-validation failure rejects the entire change set. Rejected, unsupported, and unknown outcomes report zero confirmed affected rows and no returning rows; inspect `result.response` for the actual status and failure information. Returning results correspond to the input statement order; statements without `RETURNING` have empty columns and rows. No post-commit read is used to construct them.

At most 1,000 statements and 1,000 mutated records are accepted in total. Maryk's payload and backend work limits also apply. Limits never split a SQL change set into several commits. A model/key can be mutated only once in the batch; overlapping predicates or repeated writes to the same record are rejected during preparation. Hard deletion is unsupported in atomic SQL.

`sql.atomicWriteCapabilities` reports the statement ceiling and the datastore's
atomic limits; it is `null` for unsupported stores. The new defaults allow larger
batches of small records, while payload and storage-work budgets still bound
expensive changes. SQL execution budgets apply in addition to those ceilings.
Execution-budget and atomic-count failures expose `SqlLimitException.limit`, `maximum`, and
`actual` (when known). Commit-time limits are reported through
`result.response.failure`. The planning deadline does not cover the later commit;
a commit timeout or cancellation can leave an uncertain outcome.

Preparation reads stored data before submitting the change set. Statements do not read each other's staged writes. Target version guards do not protect against newly inserted matching records or changes to other rows used by an insert query or subquery. This API provides all-or-nothing writes over the planned targets, not a general read/write transaction or serializable predicate protection. Empty selections produce a successful zero-row result, with `request == null` and no stored receipt.

`ON CONFLICT` requires ordinary serializable SQL execution and is rejected by prepared atomic change sets. Interactive transaction scopes support repeated writes and hard deletion.

Joined UPDATE FROM and DELETE USING also require serializable execution and are
rejected by prepared atomic change sets. Prefer `tx.execute` for statements that
depend on staged data. Executing a retained atomic change set inside the same
store's scoped transaction stages it in the outer journal: `AtomicStatus.Staged`,
its affected count and RETURNING values are provisional. Resolve the outer
transaction after an uncertain commit; the inner change-set ID has no independent
receipt. Reads performed during preparation outside that scope remain unprotected.

## Prepared mutations, embedded fields and upsert

`sql.prepareMutation(statement)` parses and binds without reading records. Its
parameter metadata is available immediately; execution reads current data and
starts autocommit, or joins the owning SQL transaction. Preparing is different
from retaining a previously selected atomic change set. Each execution of a
prepared mutation is a new write. Resolve an uncertain outcome through its exact
returned transaction handle, rather than executing the mutation again.

```kotlin
import maryk.sql.prepareMutation

val prepared = sql.prepareMutation(
    "UPDATE Person SET address.city = ? WHERE __key = ? RETURNING address.city",
)
val changed = prepared.execute(listOf(SqlValue.Text("Utrecht"), personKey))
```

Embedded scalar paths work in INSERT and UPDATE. Missing embedded parents are
created only when non-NULL leaf values are supplied. Updating an existing parent
preserves its other fields, including complex fields. Required leaves reject NULL;
optional leaves can be cleared. Model validation, final fields, uniqueness and
version guards still apply. No omitted model defaults are filled implicitly. Recursive embedded definitions
stop automatic flattening at the cycle and expose that boundary as non-scalar,
so other columns remain usable without generating an infinite catalog.

```sql
INSERT INTO Person (email, name) VALUES ('ada@example.test', 'Ada')
ON CONFLICT (email) DO UPDATE SET name = excluded.name
RETURNING __key, UPPER(name) AS display_name, __version;
```

A conflict target must be `__key`, the complete untransformed property key in its
defined order, or one unique scalar property. `DO NOTHING` skips matching rows;
`DO UPDATE SET` can read existing fields and the proposed `excluded` fields.
NULL unique values do not match each other. Repeated input targets are skipped by
DO NOTHING; DO UPDATE rejects targeting a record twice within one statement.
`excluded.__version` is unavailable: an incoming row has no committed version.
Use target `__version` when an assignment needs the observed stored version.

Other uniqueness violations reject the complete statement. Selection and writes
share the transaction's revision fence, so a concurrent insertion cannot silently
escape conflict protection. Conflict selection observes live rows; an occupied
soft-deleted key can still reject insertion. Use UNDELETE explicitly when restoring
such a record. There is no automatic resurrection or overwrite of another key.

## Joined mutations

```sql
UPDATE Invoice AS i SET customerName = c.name
FROM Customer c WHERE i.customer = c.__key
RETURNING i.__key, i.customerName;

DELETE FROM Invoice AS i USING Customer c
WHERE i.customer = c.__key AND c.inactive = TRUE
RETURNING i.__key;
```

Joined mutations run in the same serializable transaction as their reads. They
support source joins, derived queries and UNNEST through the shared query engine.
WHERE remains required. Assignments may read target and source scalar columns;
RETURNING refers only to the target and reports its resulting values. Qualify
ambiguous columns. More than one source match for a target rejects the statement
before staging writes, even when all matches would assign identical values. No
source match changes no record. Normal mutation, fetched-row, request, memory and
time budgets apply; limits never split a statement into partial commits.

Prepared mutations and interactive scopes support these forms. The immutable
`prepareAtomic` API rejects joined mutations because cross-model selection needs
the serializable transaction read fence. Read-only transactions reject them.
`DELETE HARD FROM … USING …` is supported by transactional SQL and follows the
same target/version rules; hard-delete selection includes soft-deleted records in
its sources. SQL does not add schema constraints or implicit defaults.

## Interactive transaction tools

The CLI retains one SQL session per connected store:

```text
sql --write BEGIN
sql --write INSERT INTO Person (name) VALUES ('Ada')
sql SELECT name FROM Person
sql --write SAVEPOINT draft
sql --write UPDATE Person SET name = 'Lin' WHERE name = 'Ada'
sql --write ROLLBACK TO draft
sql --write COMMIT
```

Transaction commands require an interactive connection and --write. The scan
policy chosen at BEGIN remains fixed until the transaction ends. Disconnect rolls
back staging; uncertain commits must be resolved before changing connections.
The desktop console accepts the same commands in write mode, shows active
transaction state, and retains the session while the modal is closed. Disconnect
rolls back staging and refuses to discard an unresolved or submitting write.

`tx.prepare(statement).execute(...).rows` provides prepared streaming reads within
a SQL transaction. Its single collector owns the SQL scope lock until cleanup;
commit, rollback and savepoints wait for that collection. Cancellation releases
the lock and leaves the transaction available. `tx.prepareMutation(...)` provides
prepared scoped writes under the same lock. The transaction lifetime continues
to apply while results are consumed.
