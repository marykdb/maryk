package maryk.core.query.responses.updates

import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.query.RequestContext

/** Indexed type of update responses */
enum class UpdateResponseType(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<UpdateResponseType>, IsCoreEnum, TypeEnum<IsUpdateResponse<*>> {
    Addition(1u),
    Change(2u),
    Removal(3u),
    OrderedKeys(4u),
    InitialValues(5u),
    InitialChanges(6u);

    companion object : IndexedEnumDefinition<UpdateResponseType>(
        UpdateResponseType::class, { entries }
    )
}

internal val mapOfUpdateResponses: Map<UpdateResponseType, EmbeddedObjectDefinition<out IsUpdateResponse<*>, *, RequestContext, RequestContext>> = mapOf(
    UpdateResponseType.Addition to EmbeddedObjectDefinition(dataModel = { AdditionUpdate }),
    UpdateResponseType.Change to EmbeddedObjectDefinition(dataModel = { ChangeUpdate }),
    UpdateResponseType.Removal to EmbeddedObjectDefinition(dataModel = { RemovalUpdate }),
    UpdateResponseType.OrderedKeys to EmbeddedObjectDefinition(dataModel = { OrderedKeysUpdate }),
    UpdateResponseType.InitialValues to EmbeddedObjectDefinition(dataModel = { InitialValuesUpdate }),
    UpdateResponseType.InitialChanges to EmbeddedObjectDefinition(dataModel = { InitialChangesUpdate })
)
