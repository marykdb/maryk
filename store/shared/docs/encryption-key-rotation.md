# Encryption key rotation

`KeyringFieldEncryptionProvider` is an additive envelope around existing
`FieldEncryptionProvider` implementations. New field values are encrypted with
the active provider and stored in a versioned envelope containing its key ID.
The configured providers decrypt envelopes for both active and retained keys.
Unwrapped ciphertext is sent to `legacyProvider`; retain that provider until
the oldest stored data is known to have been migrated. If the existing store
already writes contextual MKE2 fields without keyring envelopes, configure that
existing `ContextualFieldEncryptionProvider` as `legacyProvider` so those values
remain readable while rotation upgrades them.

## Key material is application-owned

The provider and keyring objects do not persist encryption keys. Load encryption
and deterministic-token keys from durable secret storage before opening a store,
and reconstruct the same key IDs and providers after every restart. Losing an
encryption key makes the fields written with it unreadable; losing a retained
token key can make existing sensitive unique-index entries unreachable and can
weaken duplicate detection during rotation.

Do not generate replacement keys at process startup or store raw key bytes beside
the database. Back up the secret-store entries independently, restrict access to
the Maryk process, and restore/test old keys before removing them from the active
deployment. Key IDs in envelopes are selectors, not secret material and not a
substitute for key backup.

## Rotation sequence

1. Add the new provider to the keyring. Keep the old provider and make the new
   key ID active.
2. Keep previous deterministic-token key IDs in `tokenReadKeyIds`. Writes use
   only the active token; reads and unique-conflict checks try active then the
   retained candidates. This avoids permanent duplicate unique rows while
   allowing old rows to remain addressable.
3. Run `runReEncryptionBatch` repeatedly, persisting the returned
   `ReEncryptionState` after each call. Its `read`, `write`, and state storage
   callbacks are supplied by the application/backend integration. Each
   `EncryptedFieldRecord` must contain the model ID, record key, property
   reference, and complete persisted field payload beginning with MKE1 or MKE2;
   exclude backend version bytes. The write callback receives a complete MKE2
   replacement payload. It must be idempotent because a failure before state
   persistence can replay a batch.
4. Rebuild or otherwise migrate remaining deterministic unique-index rows to
   the active token. Do not remove previous token keys before this has finished.
5. After all encrypted payloads and index rows have migrated, remove the old
   provider and, last, the legacy fallback.

The generic re-encryption loop deliberately does not provide record discovery,
transaction, or cursor persistence itself: the datastore integration owns
atomicity, ordering, and durable job state. Its record identity fields are
required because MKE2 authenticates the model, key, and property reference; do
not reconstruct or substitute them during rotation. It is not a turnkey atomic
re-encryption operation: run one backend at a time, persist state after every
batch, and make writes idempotent so a crash can safely replay the current batch.
Keep the retained providers and token keys configured while historic rows and
unique indexes are being rebuilt.
