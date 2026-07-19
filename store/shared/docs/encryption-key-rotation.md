# Encryption key rotation

`KeyringFieldEncryptionProvider` is an additive envelope around existing
`FieldEncryptionProvider` implementations. New field values are encrypted with
the active provider and stored in a versioned envelope containing its key ID.
The configured providers decrypt envelopes for both active and retained keys.
Unwrapped ciphertext is sent to `legacyProvider`; retain that provider until
the oldest stored data is known to have been migrated.

## Rotation sequence

1. Add the new provider to the keyring. Keep the old provider and make the new
   key ID active.
2. Keep previous deterministic-token key IDs in `tokenReadKeyIds`. Writes use
   only the active token; reads and unique-conflict checks try active then the
   retained candidates. This avoids permanent duplicate unique rows while
   allowing old rows to remain addressable.
3. Run `runReEncryptionBatch` repeatedly, persisting the returned
   `ReEncryptionState` after each call. Its `read`, `write`, and state storage
   callbacks are supplied by the application/backend integration. `write` must
   be idempotent because a failure before state persistence can replay a batch.
4. Rebuild or otherwise migrate remaining deterministic unique-index rows to
   the active token. Do not remove previous token keys before this has finished.
5. After all encrypted payloads and index rows have migrated, remove the old
   provider and, last, the legacy fallback.

The generic re-encryption loop deliberately does not provide transaction or
cursor persistence itself: the datastore integration owns atomicity, ordering,
and durable job state.
