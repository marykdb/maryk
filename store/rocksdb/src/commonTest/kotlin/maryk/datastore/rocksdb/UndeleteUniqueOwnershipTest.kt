package maryk.datastore.rocksdb

import maryk.createTestDBFolder
import maryk.deleteFolder
import kotlinx.coroutines.test.runTest
import maryk.core.models.RootDataModel
import maryk.core.models.DataModel
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.map
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.delete
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider
import maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider
import maryk.datastore.shared.encryption.FieldEncryptionContext
import maryk.datastore.shared.encryption.SensitiveIndexTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object UndeleteUniqueDetail : DataModel<UndeleteUniqueDetail>() {
    val email by string(1u, unique = true, required = false)
}

private object UndeleteCurrentUniqueRecord : RootDataModel<UndeleteCurrentUniqueRecord>() {
    val email by string(1u, unique = true, required = false)
    val secret by string(2u, unique = true, sensitive = true, required = false)
    val detail by embed(3u, dataModel = { UndeleteUniqueDetail }, required = false)
    val mapping by map(4u, keyDefinition = StringDefinition(), valueDefinition = StringDefinition(unique = true), required = false)
    val nested by map(5u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { UndeleteUniqueDetail }), required = false)
    val alias by string(6u, unique = true, required = false)
}

class UndeleteUniqueOwnershipTest {
    @Test
    fun undeleteClaimsCurrentPlaintextAndEncryptedValuesWithAndWithoutHistory() = runTest {
        for (history in listOf(false, true)) {
            val folder = createTestDBFolder("undelete-current-unique-$history")
            val provider = RejectingUniqueDecryptProvider(AesGcmHmacSha256EncryptionProvider(ByteArray(32) { it.toByte() }, ByteArray(32) { (it + 32).toByte() }))
            val store = RocksDBDataStore.open(relativePath = folder, dataModelsById = mapOf(1u to UndeleteCurrentUniqueRecord),
                keepAllVersions = history, fieldEncryptionProvider = provider)
            try {
                checkUnrelatedPlaintextEdit(store, provider)
                checkCurrentUniqueClaims(store, history)
                checkCurrentUniqueCollision(store)
                checkDeletedComplexUniqueClaims(store)
                checkRemovedComplexUniqueClaims(store)
                checkNestedMapUniqueClaims(store, history)
                checkCrossPropertyUniqueClaims(store)
            } finally {
                store.close()
                deleteFolder(folder)
            }
        }
    }
}

private suspend fun checkCrossPropertyUniqueClaims(store: IsDataStore) {
    for (operation in listOf("soft-delete", "hard-delete", "remove")) {
        val sourceValue = "source-$operation"
        val ownedValue = "owned-$operation"
        val subject = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
            UndeleteCurrentUniqueRecord.create { email with sourceValue; alias with ownedValue }
        )).statuses.single())
        val owner = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
            UndeleteCurrentUniqueRecord.create { email with ownedValue }
        )).statuses.single())
        when (operation) {
            "soft-delete" -> assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
                subject.key.change(ObjectSoftDeleteChange(true))
            )).statuses.single())
            "hard-delete" -> assertIs<DeleteSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.delete(
                subject.key, hardDelete = true
            )).statuses.single())
            else -> assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
                subject.key.change(Change(UndeleteCurrentUniqueRecord.ref { email } with null))
            )).statuses.single())
        }
        assertEquals(ownedValue, store.execute(UndeleteCurrentUniqueRecord.get(owner.key)).values.single().values { email })
        assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
            UndeleteCurrentUniqueRecord.create { email with ownedValue }
        )).statuses.single(), "$operation must preserve another record's claim on the earlier unique property")
    }
}

private suspend fun checkCurrentUniqueClaims(store: IsDataStore, history: Boolean) {
    val first = UndeleteCurrentUniqueRecord.create { email with "old"; secret with "old-secret" }
    val added = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(first)).statuses.single())
    assertIs<DeleteSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.delete(added.key)).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(added.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { email } with "new", UndeleteCurrentUniqueRecord.ref { secret } with "new-secret")
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "old"; secret with "old-secret" }
    )).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        added.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())
    val restored = store.execute(UndeleteCurrentUniqueRecord.get(added.key)).values.single()
    assertEquals("new", restored.values { email })
    assertEquals("new-secret", restored.values { secret })
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "new" }
    )).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { secret with "new-secret" }
    )).statuses.single())
    if (history) {
        assertEquals(first, store.execute(UndeleteCurrentUniqueRecord.get(added.key, toVersion = added.version)).values.single().values)
    }

    // Removing optional unique values while deleted must not resurrect their old claims.
    store.execute(UndeleteCurrentUniqueRecord.delete(added.key))
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(added.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { email } with null, UndeleteCurrentUniqueRecord.ref { secret } with null)
    ))).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        added.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "new"; secret with "new-secret" }
    )).statuses.single())
}

private suspend fun checkCurrentUniqueCollision(store: IsDataStore) {
    val added = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "before"; secret with "before-secret" }
    )).statuses.single())
    store.execute(UndeleteCurrentUniqueRecord.delete(added.key))
    store.execute(UndeleteCurrentUniqueRecord.change(added.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { email } with "after", UndeleteCurrentUniqueRecord.ref { secret } with "after-secret")
    )))
    val owner = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "after"; secret with "after-secret" }
    )).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        added.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())
    assertTrue(store.execute(UndeleteCurrentUniqueRecord.get(added.key, filterSoftDeleted = false)).values.single().isDeleted)
    assertEquals("after", store.execute(UndeleteCurrentUniqueRecord.get(owner.key)).values.single().values { email })
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "before"; secret with "before-secret" }
    )).statuses.single())
}

private suspend fun checkDeletedComplexUniqueClaims(store: IsDataStore) {
    val added = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { detail with UndeleteUniqueDetail.create { email with "embedded-old" } }
    )).statuses.single())
    store.execute(UndeleteCurrentUniqueRecord.delete(added.key))
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(added.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { detail } with UndeleteUniqueDetail.create { email with "embedded-new" })
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { detail with UndeleteUniqueDetail.create { email with "embedded-new" } }
    )).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        added.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())

    // A qualifier introduced only while deleted has no previous unique-index marker.
    val dynamic = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { mapping with emptyMap() }
    )).statuses.single())
    store.execute(UndeleteCurrentUniqueRecord.delete(dynamic.key))
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(dynamic.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { mapping } with mapOf("slot" to "dynamic-current"))
    ))).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        dynamic.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { mapping with mapOf("slot" to "dynamic-current") }
    )).statuses.single())
}

private suspend fun checkRemovedComplexUniqueClaims(store: IsDataStore) {
    val child = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { detail with UndeleteUniqueDetail.create { email with "removed-child" } }
    )).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(child.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { detail } with UndeleteUniqueDetail.create { })
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { detail with UndeleteUniqueDetail.create { email with "removed-child" } }
    )).statuses.single())

    val mapped = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { mapping with mapOf("slot" to "removed-map") }
    )).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(mapped.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { mapping } with emptyMap())
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { mapping with mapOf("slot" to "removed-map") }
    )).statuses.single())

    // Transient claims written and removed within one change must be released too.
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(child.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { detail } with UndeleteUniqueDetail.create { email with "transient-child" },
            UndeleteCurrentUniqueRecord.ref { detail } with UndeleteUniqueDetail.create { })
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { detail with UndeleteUniqueDetail.create { email with "transient-child" } }
    )).statuses.single())
}

private suspend fun checkNestedMapUniqueClaims(store: IsDataStore, history: Boolean) {
    fun nestedValue(value: String?) = UndeleteCurrentUniqueRecord.create {
        nested with mapOf("fixed" to UndeleteUniqueDetail.create { if (value != null) email with value })
    }

    val removed = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        nestedValue("nested-removed")
    )).statuses.single())
    val replaced = store.execute(UndeleteCurrentUniqueRecord.change(removed.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { nested } with mapOf("fixed" to UndeleteUniqueDetail.create { }))
    ))).statuses.single()
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(replaced, "Nested-map subtree replacement: $replaced")
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        nestedValue("nested-removed")
    )).statuses.single(), "Replacing a nested-map subtree releases its leaf's unique claim")

    val original = nestedValue("nested-before")
    val restored = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(original)).statuses.single())
    assertIs<DeleteSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.delete(restored.key)).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(restored.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { nested } with mapOf("fixed" to UndeleteUniqueDetail.create { email with "nested-current" }))
    ))).statuses.single())
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(nestedValue("nested-before"))).statuses.single())
    val activated = store.execute(UndeleteCurrentUniqueRecord.change(restored.key.change(ObjectSoftDeleteChange(false)))).statuses.single()
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(activated, "Nested-map activation with history=$history: $activated")
    assertEquals(nestedValue("nested-current"), store.execute(UndeleteCurrentUniqueRecord.get(restored.key)).values.single().values)
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        nestedValue("nested-current")
    )).statuses.single(), "Undelete claims the nested-map leaf's current value")
    if (history) {
        assertEquals(original, store.execute(UndeleteCurrentUniqueRecord.get(restored.key, toVersion = restored.version)).values.single().values)
    }

    val deleted = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        nestedValue("nested-collision-before")
    )).statuses.single())
    assertIs<DeleteSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.delete(deleted.key)).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(deleted.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { nested } with mapOf("fixed" to UndeleteUniqueDetail.create { email with "nested-owned" }))
    ))).statuses.single())
    val owner = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(nestedValue("nested-owned"))).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        deleted.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single(), "Nested-map activation rejects another current owner")
    assertTrue(store.execute(UndeleteCurrentUniqueRecord.get(deleted.key, filterSoftDeleted = false)).values.single().isDeleted)
    assertEquals(nestedValue("nested-owned"), store.execute(UndeleteCurrentUniqueRecord.get(owner.key)).values.single().values)

    // Removing the conflicted subtree while deleted permits activation and keeps
    // the other record's ownership intact.
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(deleted.key.change(
        Change(UndeleteCurrentUniqueRecord.ref { nested } with emptyMap())
    ))).statuses.single())
    assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
        deleted.key.change(ObjectSoftDeleteChange(false))
    )).statuses.single())
    assertIs<ValidationFail<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(nestedValue("nested-owned"))).statuses.single())
}

private class RejectingUniqueDecryptProvider(private val delegate: AesGcmHmacSha256EncryptionProvider) :
    ContextualFieldEncryptionProvider by delegate, SensitiveIndexTokenProvider by delegate {
    var reject = false
    override suspend fun decrypt(context: FieldEncryptionContext, value: ByteArray, offset: Int, length: Int): ByteArray {
        check(!reject) { "Unchanged encrypted unique value must not be decrypted" }
        return delegate.decrypt(context, value, offset, length)
    }
}

private suspend fun checkUnrelatedPlaintextEdit(store: IsDataStore, provider: RejectingUniqueDecryptProvider) {
    val added = assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "unrelated-before"; secret with "untouched-secret" }
    )).statuses.single())
    provider.reject = true
    try {
        assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(added.key.change(
            Change(UndeleteCurrentUniqueRecord.ref { email } with "unrelated-after")
        ))).statuses.single())
    } finally {
        provider.reject = false
    }
    assertEquals("untouched-secret", store.execute(UndeleteCurrentUniqueRecord.get(added.key)).values.single().values { secret })
    provider.reject = true
    try {
        assertIs<ChangeSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.change(
            added.key.change(ObjectSoftDeleteChange(true))
        )).statuses.single())
    } finally {
        provider.reject = false
    }
    assertTrue(store.execute(UndeleteCurrentUniqueRecord.get(added.key, filterSoftDeleted = false)).values.single().isDeleted)
    assertIs<AddSuccess<UndeleteCurrentUniqueRecord>>(store.execute(UndeleteCurrentUniqueRecord.add(
        UndeleteCurrentUniqueRecord.create { email with "unrelated-after"; secret with "untouched-secret" }
    )).statuses.single())
}
