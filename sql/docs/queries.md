# SQL queries and values

Start with the [SQL overview](../README.md) for installation and a first query.

## Models, tables, and values

By default, registered root models become tables in the `public` schema. Unquoted identifiers fold ASCII letters to lowercase; double-quoted identifiers preserve case. SQL string literals use single quotes and escape a quote by doubling it.

Provide a catalog to rename tables or expose selected columns:

```kotlin
import maryk.sql.SqlCatalog
import maryk.sql.SqlTable

val catalog = SqlCatalog(listOf(
    SqlTable("people", Person, exposedColumns = setOf("name", "age")),
))
val sql = MarykSql.create(dataStore, catalog, SqlOptions(allowTableScan = true))
```

`__key` is the model's typed record key and `__version` is its current Maryk version. Both system columns remain available when an explicit column set is used. Scalar embedded properties are exposed as dotted paths. Reference values retain their target model identity; reading a reference does not automatically join its target. Enums retain their definition identity and index.

Stored missing properties become SQL `NULL`. The layer reads stored values without applying wrapper conversions or filling model defaults. A default actually stored when the record was created remains a real value. An existing record with absent selected properties still contributes a row.

Use explicit scalar columns for complex models. Scalar collections, embedded record maps and supported nested collection paths can be expanded with `UNNEST`. Collection values have no whole-value comparison/grouping mapping; multi-types, value objects and spatial properties remain unsupported. Unsupported columns are reported explicitly, including when a wildcard would include them.

`SqlValue` represents booleans, signed and unsigned 64-bit integers, arbitrary-size fixed-scale decimals, finite doubles, strings, bytes, dates, times, timestamps, model keys, enums, and `NULL`. `SqlValue.of(...)` converts ordinary scalar Kotlin values. Keys and enums have typed constructors.

Numeric `of(...)` overloads preserve the statically known Kotlin type. JavaScript erases primitive numeric types stored as `Any` or `Number`: the generic overload treats Int-range integral values as integers and other numbers as floating-point values. Use `SqlValue.Int64` or `SqlValue.Float64` explicitly when the numeric category must survive type erasure. Non-finite floating values are rejected.

## Query surface

The query layer supports:

- `SELECT` (including explicit `ALL`), expressions and aliases, scalar and qualified wildcards, optional `FROM`, `WHERE`, `DISTINCT`, `ORDER BY`, explicit `NULLS FIRST/LAST`, `LIMIT`, `OFFSET … ROWS`, and `FETCH FIRST/NEXT … ROWS ONLY`. Omitting the fetch count means one row. Parenthesized queries retain their own ordering and pagination.
- Positional parameters, exact literals, temporal literals, arithmetic, concatenation, comparisons, `AND/OR/NOT`, `IS NULL/TRUE/FALSE/UNKNOWN`, `IS DISTINCT FROM`, `IN`, `BETWEEN`, and `LIKE … ESCAPE`.
- Searched and simple `CASE`, `CAST`, `COALESCE`, `NULLIF`, numeric rounding functions, string functions, and `DATE_PART`.
- `COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, `GROUP BY`, `HAVING`, aggregate `DISTINCT`, and aggregate `FILTER (WHERE …)`.
- Nonrecursive CTEs, derived tables, `VALUES`, `UNION`, `INTERSECT`, and `EXCEPT`, with `ALL` where specified.
- Explicit `INNER`, `LEFT`, and `CROSS` joins, with SQL predicate and null-extension semantics.
- Uncorrelated and bounded correlated scalar, `EXISTS`, and `IN` subqueries. A scalar subquery returns `NULL` for no rows and fails for more than one row.
- Window ranking, navigation and aggregate functions, named windows, bounded ROWS frames and numeric-offset RANGE frames.
- Scalar and supported complex collection expansion through UNNEST, with lateral joins and optional ordinality.
- Queryable information_schema table, column and index metadata.
- `EXPLAIN` and `EXPLAIN ANALYZE`, alongside `PreparedSqlQuery.explain()` and execution counters.

`EXISTS` stops at the first result row and skips projections and ordering that do not affect row existence. Distinct values and set-operation operands are still evaluated when needed to determine cardinality, including after an offset.

Examples:

```sql
SELECT category,
       COUNT(*) AS invoices,
       SUM(amount) FILTER (WHERE paid IS TRUE) AS paid_total,
       AVG(amount) AS average
FROM Invoice
GROUP BY category
HAVING COUNT(*) >= 2
ORDER BY paid_total DESC NULLS LAST;
```

```sql
WITH totals AS (
    SELECT customer, SUM(amount) AS total
    FROM Invoice
    GROUP BY customer
)
SELECT p.name, t.total
FROM Person p
LEFT JOIN totals t ON p.__key = t.customer
ORDER BY t.total DESC NULLS LAST;
```

```sql
SELECT name FROM Person
WHERE __key IN (SELECT customer FROM Invoice WHERE amount > 100);
```

The layer does not provide DDL, JDBC, recursive CTEs, arbitrary lateral derived tables, live SQL subscriptions, or vendor-specific dialect compatibility. Unsupported forms fail explicitly. Schema creation and migrations remain Maryk APIs.

## Numeric and null semantics

Comparisons involving `NULL` produce unknown. `WHERE`, `HAVING`, and `ON` retain only true matches. `NOT IN` with a null candidate can therefore produce unknown. `GROUP BY`, `DISTINCT`, and set operations use SQL null equality.

Floating arithmetic overflow reports `SqlErrorCode.ARITHMETIC`; non-finite results are never emitted.

Exact arithmetic does not pass through `Double`. Signed integers, unsigned integers, and decimals compare by mathematical value. Decimal scales may differ without producing different groups. Mixing approximate floating-point numbers with exact numbers requires an explicit cast.

`SUM` of exact values uses `Decimal`, avoiding the stored property's narrow numeric range. `AVG` and division use decimal scale at least six, retaining greater input scale up to 18, with ties rounded to even. `COUNT` of an empty input is zero; other aggregates return `NULL`. A global aggregate over empty input produces one row; grouped empty input produces none.

Text ordering uses Kotlin's ordinal UTF-16 comparison. `LIKE` and substring character boundaries handle Unicode code points. SQL null ordering defaults to last for ascending and first for descending order. Add an explicit unique tie-breaker when a deterministic total order matters.

Query operands are not writes: a null parameter is legal against a required field, and comparisons outside a property's stored range remain meaningful. Native pushdown is used only when conversion preserves the SQL predicate; remaining predicates execute locally. Text range comparisons currently stay local; the SQL planner does not advertise text-range pushdown. Text equality can still use indexes.

Key `IN` lists containing only literals or parameters can use native key access. Computed candidates remain local so an earlier match can skip their evaluation. Native filters and hash joins also keep later predicates local when an earlier computed condition could fail; these queries may require an allowed table scan.

Floating-point comparisons against zero also stay local, preserving SQL equality between positive and negative zero across stores.

## Catalog discovery

The reserved `information_schema` schema is generated from the exposed immutable
catalog and needs no datastore reads:

```sql
SELECT table_schema, table_name, model_name FROM information_schema.tables;
SELECT column_name, data_type, is_nullable, is_writable, is_unique
FROM information_schema.columns WHERE table_name = 'Person';
SELECT is_primary, encoding FROM information_schema.indexes
WHERE table_name = 'Person';
```

`tables` also contains table_type. `columns` contains table_schema, table_name,
ordinal_position and is_system. `indexes` contains table_schema, table_name and
index_ordinal; ordinal zero describes the record-key encoding. Index encodings
preserve the Maryk definition identity, including transformed keys. The catalog
reflects locally exposed columns and models and does not replace Remote server
authorization. Feature capabilities report metadata, windows, correlation,
collection expansion, scalar returning expressions, embedded writes and
transactional upsert alongside store consistency support.

## Window functions and correlated queries

Supported windows are ROW_NUMBER, RANK, DENSE_RANK, NTILE, LAG, LEAD,
FIRST_VALUE, LAST_VALUE, NTH_VALUE, COUNT, SUM, AVG, MIN and MAX. OVER accepts
PARTITION BY and ORDER BY with ordinary SQL null placement. Named definitions use
`WINDOW name AS (...)` and `OVER name`; definitions belong to one query scope.
Window inheritance and frame exclusion are not supported.

LAG/LEAD accept a constant nonnegative integer offset and a scalar default.
NTILE requires a positive constant integer bucket count; larger buckets come first.
NTH_VALUE uses a positive one-based constant integer index. Value functions respect
the frame and include NULL values; an empty frame or missing nth element returns NULL.

Without ordering, the default aggregate frame covers the full partition. With
ordering, it includes peers through the current ordering value. ROWS frames accept
CURRENT ROW, UNBOUNDED PRECEDING/FOLLOWING and constant nonnegative integer offsets.
RANGE frames support peer boundaries and unbounded boundaries; numeric offsets
require exactly one numeric ordering expression. RANGE offsets may be fractional;
offsets outside the floating numeric range report a structured arithmetic error;
temporal INTERVAL offsets are not supported. Descending order reverses offset direction.
Invalid boundary categories are rejected; a valid frame outside the partition is empty.
Parameters are allowed for constant offsets and bucket counts.

Full and cumulative aggregate frames use incremental state. Other frames evaluate
only their member rows and charge each visit to the expression work budget, so wide
sliding frames can reach that budget. Partition count uses the normal group budget;
row, expression, memory and time budgets apply throughout execution.

```sql
SELECT customer, amount,
       ROW_NUMBER() OVER (PARTITION BY customer ORDER BY amount DESC) AS rank,
       SUM(amount) OVER (PARTITION BY customer ORDER BY issued
                        ROWS UNBOUNDED PRECEDING) AS running_total
FROM Invoice;
```

Windows run after WHERE and before final DISTINCT, ordering and pagination. They
are unavailable in WHERE, HAVING, mutation assignments and RETURNING. To apply a
window to grouped output, put the aggregate in a CTE or derived relation first.
There is no promise of a stable ordering among tied rows unless the window order
contains a distinguishing key.

Correlated scalar, EXISTS and IN subqueries retain their lexical outer bindings.
Only uncorrelated results are cached, including CTEs and set-operation operands.
Join optimizations use local inputs; outer-scope join predicates retain their lexical
row through evaluation. Correlated evaluations share execution
budgets and the same snapshot or transaction fence; safe outer key predicates can
use native lookups. Scalar cardinality and SQL NULL/NOT IN rules remain unchanged.
Correlation can cost many requests and therefore fails explicitly on budget
exhaustion. Arbitrary correlated derived tables remain unsupported.

## Collection expansion

```sql
SELECT p.__key, e.ordinality, e.value
FROM Person p CROSS JOIN UNNEST(p.aliases) WITH ORDINALITY e;

SELECT p.__key, e.entry_key, e.value
FROM Person p LEFT JOIN UNNEST(p.labels) e ON TRUE;
```

UNNEST accepts scalar Maryk lists, sets, maps and incrementing maps, including
embedded collection paths, or an explicit `SqlValue.Collection` parameter. Scalar
entries expose `value`; maps also expose their typed `entry_key`, which is NULL for
lists and sets. WITH ORDINALITY adds one-based ordinality. Lists retain stored order;
sets and maps do not promise an ordering. Keep the parent __key and map entry_key
when identifying expanded records. List ordinality is not a persistent element ID.

Maps of embedded records expose typed flattened fields such as `e.value.label`.
Collections within those records and maps whose values are collections can feed
another UNNEST. SQL uses the model's types, including reference and enum domains:

```sql
SELECT p.__key, e.entry_key, e.value.label, child.value
FROM Person p
CROSS JOIN UNNEST(p.details) e
LEFT JOIN UNNEST(e.value.children) child ON TRUE;

SELECT p.__key, groupEntry.entry_key, member.value
FROM Person p
CROSS JOIN UNNEST(p.groups) groupEntry
CROSS JOIN UNNEST(groupEntry.value) member;
```

Missing and empty collections produce zero rows in a CROSS/INNER join. LEFT JOIN
preserves the parent with NULL extended columns. Expansion is lateral to preceding
sources and shares all normal budgets. The SQL layer currently rejects embedded-record and collection-valued
list/set elements during binding; this is a SQL mapping limitation, not a general
Maryk storage limitation. Unsupported opaque or
recursive record boundaries are also rejected. Collection nesting is bounded at
32 levels in both schema inspection and value traversal, and 100,000 visited scalar/collection cells per value, including map keys
and embedded fields, in addition to query relation and memory/work limits. This
also bounds repeated traversal of shared nested parameter graphs.

Explicit collection parameters may contain nested collections; their entry keys
must remain scalar, and only maps carry non-NULL keys. Parameters expose ANY
value/key metadata because element types arrive at execution. Nested parameter
collections can feed another UNNEST. Typed record field expansion requires a model
schema. Grouping/comparing collections as a whole remains unsupported. CLI and app
queries use these same model collection rules; collection parameters use the Kotlin API.
