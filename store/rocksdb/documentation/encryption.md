# RocksDB field encryption

Mark sensitive properties in the model and provide the encryption implementation
when opening the store. Keep the keys independently recoverable so restored
backups can be read.

`RocksDBDataStore.open` accepts a `fieldEncryptionProvider` argument using the shared encryption contract
`maryk.datastore.shared.encryption.FieldEncryptionProvider`.

Use `sensitive = true` on simple property definitions:

```kotlin
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string

object SecretNote : RootDataModel<SecretNote>() {
    val message by string(index = 1u, sensitive = true)
}
```

Generate key material once during provisioning and retain it securely outside the
store. Load the retained keys on subsequent opens. Initial provider creation is:

```kotlin
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider

val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()
val encryptionProvider = AesGcmHmacSha256EncryptionProvider(
    encryptionKey = keyMaterial.encryptionKey,
    tokenKey = keyMaterial.tokenKey,
)
```

Pass this provider when opening the store:

```kotlin
import maryk.datastore.rocksdb.RocksDBDataStore

RocksDBDataStore.open(
    relativePath = "secret-notes.db",
    dataModelsById = mapOf(1u to SecretNote),
    fieldEncryptionProvider = encryptionProvider,
).use { /* execute requests */ }
```

Notes:

- Sensitive values are encrypted in table payloads (latest + historic).
- Reads auto-decrypt based on an encrypted payload marker.
- Supported for simple value properties.
- Sensitive+`unique` is supported when `fieldEncryptionProvider` also implements
  `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider`.
- Sensitive properties cannot contribute to primary keys or secondary indexes,
  including composite and transformed indexes. Use a separate explicit blind-index
  field when search is required. Custom indexes without a dependency contract are
  rejected when the model contains sensitive properties.
- Earlier versions could accept unsafe composite indexes. This validation does not
  erase existing plaintext key/index bytes or physical backups. Recover affected
  data into a fresh store with safe definitions, then securely retire old copies.

For key rotation, callback ownership and durability requirements, follow the
[shared encryption rotation guide](../../shared/docs/encryption-key-rotation.md).
Physical backups preserve encrypted bytes and do not contain your keys; see
[RocksDB backups](backups.md). For deletion semantics, see
[hard deletion and retained history](history-and-transactions.md#hard-deletion).

Return to the [module overview](../README.md).
