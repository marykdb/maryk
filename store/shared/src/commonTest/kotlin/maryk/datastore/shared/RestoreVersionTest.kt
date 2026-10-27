package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.core.query.responses.UpdateResponse
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreVersionTest {
    @Test
    fun versionRequestOwnsItsKeysAndChanges() {
        val key = SimpleMarykModel.key(ByteArray(16))
        val changes = mutableListOf<IsChange>(ObjectCreate, Change(SimpleMarykModel.ref { value } with "ha original"))
        val record = DataObjectVersionedChange(key, changes = listOf(VersionedChanges(100uL, changes)))
        @Suppress("UNCHECKED_CAST")
        val update = UpdateResponse(SimpleMarykModel, InitialChangesUpdate(101uL, listOf(record))) as UpdateResponse<IsRootDataModel>
        val request = RestoreVersionRequest(listOf(update))
        key.bytes[0] = 42
        changes[1] = Change(SimpleMarykModel.ref { value } with "ha mutated")
        val owned = (request.updates.single().update as InitialChangesUpdate).changes.single()
        assertEquals(0, owned.key.bytes[0].toInt())
        assertEquals(0, request.targets.getValue(SimpleMarykModel).single().bytes[0].toInt())
        val value = owned.changes.single().changes.filterIsInstance<Change>().single().referenceValuePairs.single().value
        assertEquals("ha original", value)
    }
}
