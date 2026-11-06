package maryk.datastore.shared

import maryk.core.models.DataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Version
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RestoreCodecSchemaTest {
    @Test
    fun historyCodecCannotDropUnknownFieldsInsideMappedObjects() {
        val values = NewHistoryProfile.create {
            objects with mapOf("record" to NewHistoryLeaf.create { value with "known"; additional with "must survive" })
        }
        val record = DataObjectVersionedChange<IsRootDataModel>(NewHistoryProfile.key(values), changes = listOf(
            VersionedChanges(10uL, listOf(ObjectCreate) + values.toChanges()),
        ))
        val source = RestoreCodec(mapOf(NewHistoryProfile.Meta.name to NewHistoryProfile))
        val bytes = source.encode(NewHistoryProfile.Meta.name, record, MAX_ATOMIC_BYTES)
        val target = RestoreCodec(mapOf(OldHistoryProfile.Meta.name to OldHistoryProfile))
        assertFailsWith<ParseException> { target.decode(OldHistoryProfile.Meta.name, bytes) }
    }
}

private object OldHistoryLeaf : DataModel<OldHistoryLeaf>() {
    val value by string(1u)
}
private object NewHistoryLeaf : DataModel<NewHistoryLeaf>() {
    val value by string(1u)
    val additional by string(2u, required = false)
}
private object OldHistoryProfile : RootDataModel<OldHistoryProfile>(name = "HistorySchema") {
    val objects by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { OldHistoryLeaf }))
}
private object NewHistoryProfile : RootDataModel<NewHistoryProfile>(name = "HistorySchema", version = Version(1, 1)) {
    val objects by map(1u, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { NewHistoryLeaf }))
}
