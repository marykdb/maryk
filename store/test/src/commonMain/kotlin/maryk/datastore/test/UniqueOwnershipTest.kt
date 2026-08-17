package maryk.datastore.test

import maryk.core.properties.exceptions.AlreadyExistsException
import maryk.core.properties.types.Key
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
import maryk.core.query.responses.statuses.ValidationFail
import maryk.datastore.shared.IsDataStore
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UniqueOwnershipTest(
    private val dataStore: IsDataStore,
) {
    suspend fun finalSoftDeleteWithCollidingUniqueReleasesOwnership() {
        val keys = mutableListOf<Key<UniqueModel>>()
        try {
            val deleted = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "retired@test.com" })).statuses.single()
            )
            keys += deleted.key
            val owner = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "owned@test.com" })).statuses.single()
            )
            keys += owner.key

            assertStatusIs<ChangeSuccess<UniqueModel>>(
                dataStore.execute(
                    UniqueModel.change(
                        deleted.key.change(
                            Change(UniqueModel { email::ref } with "owned@test.com"),
                            ObjectSoftDeleteChange(true),
                        )
                    )
                ).statuses.single()
            )
            assertTrue(dataStore.execute(UniqueModel.get(deleted.key, filterSoftDeleted = false)).values.single().isDeleted)

            val replacement = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "retired@test.com" })).statuses.single()
            )
            keys += replacement.key
        } finally {
            if (keys.isNotEmpty()) dataStore.execute(UniqueModel.delete(*keys.toTypedArray(), hardDelete = true))
        }
    }

    suspend fun deletedUniqueMutationDoesNotClaimOwnership() {
        val keys = mutableListOf<Key<UniqueModel>>()
        try {
            val deleted = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "old@test.com" })).statuses.single()
            )
            keys += deleted.key
            val owner = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "owned@test.com" })).statuses.single()
            )
            keys += owner.key
            dataStore.execute(UniqueModel.delete(deleted.key))

            assertStatusIs<ChangeSuccess<UniqueModel>>(
                dataStore.execute(
                    UniqueModel.change(
                        deleted.key.change(Change(UniqueModel { email::ref } with "owned@test.com"))
                    )
                ).statuses.single()
            )

            val replacement = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "old@test.com" })).statuses.single()
            )
            keys += replacement.key
        } finally {
            if (keys.isNotEmpty()) dataStore.execute(UniqueModel.delete(*keys.toTypedArray(), hardDelete = true))
        }
    }

    suspend fun restoreCollisionKeepsDeletedRecordOwnerless() {
        val keys = mutableListOf<Key<UniqueModel>>()
        try {
            val deleted = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "claimed@test.com" })).statuses.single()
            )
            keys += deleted.key
            dataStore.execute(UniqueModel.delete(deleted.key))
            val owner = assertStatusIs<AddSuccess<UniqueModel>>(
                dataStore.execute(UniqueModel.add(UniqueModel.create { UniqueModel.email with "claimed@test.com" })).statuses.single()
            )
            keys += owner.key

            val failedRestore = assertStatusIs<ValidationFail<UniqueModel>>(
                dataStore.execute(
                    UniqueModel.change(deleted.key.change(ObjectSoftDeleteChange(false)))
                ).statuses.single()
            )
            val alreadyExists = assertIs<AlreadyExistsException>(failedRestore.exceptions.single())
            kotlin.test.assertEquals(owner.key, alreadyExists.key)
            assertTrue(dataStore.execute(UniqueModel.get(deleted.key, filterSoftDeleted = false)).values.single().isDeleted)
            assertTrue(dataStore.execute(UniqueModel.get(owner.key)).values.isNotEmpty())
        } finally {
            if (keys.isNotEmpty()) dataStore.execute(UniqueModel.delete(*keys.toTypedArray(), hardDelete = true))
        }
    }
}
