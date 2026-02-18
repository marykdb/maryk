# Maryk Store Shared

Common datastore layer used by all store engines.
Not intended as a standalone store.

## What it contains

- Unified datastore contracts and request orchestration used by all engines.
- Shared update/listener flow behavior.
- Shared versioning/query semantics.
- Shared sensitive-field encryption contracts:
  - `maryk.datastore.shared.encryption.FieldEncryptionProvider`
  - `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider`
- Built-in cross-platform provider:
  - `maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider`
  - Field encryption: AES-GCM
  - Deterministic sensitive lookup tokens: HMAC-SHA256

## Where to use

Use through concrete engines:
- [Memory store](../memory/README.md)
- [RocksDB store](../rocksdb/README.md)
- [FoundationDB store](../foundationdb/README.md)
