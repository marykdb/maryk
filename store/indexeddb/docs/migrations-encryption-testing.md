# IndexedDB Migrations, Encryption, And Testing

## Startup Migration

On open, the store compares configured model definitions with metadata stored in IndexedDB.

Supported automatic changes:

- same-model reopen;
- backfill missing encoded model definitions for older metadata;
- safe property additions;
- new current indexes on existing properties;
- new historic indexes when history is enabled.

New index backfill uses real IndexedDB scans over current snapshots and historic snapshots. It does not fall back to an in-memory store.

Unsafe model changes fail unless explicit migration handlers are supplied through `MigrationConfiguration`. Successful explicit migrations store the new model definition so later opens do not rerun them.

## Schema Upgrades

If a requested object store is missing, the database is reopened with a higher IndexedDB version and the missing stores are created. Existing data is preserved.

Open connections close themselves on `versionchange`. Blocked opens fail with a clear error.

## Encryption

The store uses Maryk's shared field encryption provider abstraction.

Behavior:

- sensitive current table values are encrypted;
- sensitive historic snapshot values are encrypted;
- reads decrypt through the provider;
- sensitive unique values use deterministic lookup tokens when supported;
- sensitive indexed values are rejected.

Browser-native providers are available for JS and WasmJS:

- `WebCryptoAesGcmHmacSha256EncryptionProvider`

The provider uses WebCrypto for AES-GCM encryption and HMAC-SHA256 lookup tokens. IndexedDB owns storage, not crypto policy.

## Tests

Node tests use `fake-indexeddb` while preserving the same IndexedDB byte-store API used in browsers.

Focused module tests:

```bash
./gradlew :store:indexeddb:jsNodeTest
./gradlew :store:indexeddb:wasmJsNodeTest
```

Browser tests:

```bash
./gradlew :store:indexeddb:jsBrowserTest
./gradlew :store:indexeddb:wasmJsBrowserTest
```

Important covered areas:

- add/change/get/delete;
- table/index/unique scans;
- historic `toVersion` get/table/index/unique reads;
- getChanges/scanChanges;
- getUpdates/scanUpdates;
- scanUpdateHistory;
- ordered scan-update refill behavior;
- fuzzy qualifier and sub-reference filters;
- any-value, normalized, multi-type, and mutable-value indexes;
- WebCrypto encryption providers;
- metadata migration and index backfill;
- hard-delete cleanup of historic secondary rows;
- JS and WasmJS IndexedDB byte-store behavior.
