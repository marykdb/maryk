package maryk.core.query.responses.updates

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.ObjectCreateModel

/** Indexed type of update responses */
enum class UpdateResponseType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<UpdateResponseType>, IsCoreEnum, TypeEnum<IsUpdateResponse<*, *>> {
    Addition(1u),
    Change(2u),
    Removal(3u),
    OrderedKeys(4u);

    companion object : IndexedEnumDefinition<UpdateResponseType>(
        UpdateResponseType::class, UpdateResponseType::values
    )
}

internal val mapOfUpdateResponses = mapOf(
    UpdateResponseType.Addition to EmbeddedObjectDefinition(dataModel = { AdditionUpdate }),
    UpdateResponseType.Change to EmbeddedObjectDefinition(dataModel = { ChangeUpdate }),
    UpdateResponseType.Removal to EmbeddedObjectDefinition(dataModel = { RemovalUpdate }),
    UpdateResponseType.OrderedKeys to EmbeddedObjectDefinition(dataModel = { OrderedKeysUpdate })
)
