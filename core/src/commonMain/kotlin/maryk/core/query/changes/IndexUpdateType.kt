package maryk.core.query.changes

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.query.RequestContext

/** Indexed type of index update */
enum class IndexUpdateType(
    override val index: UInt,
    val dataModel: IsObjectDataModel<out IsIndexUpdate>
) : IndexedEnumComparable<IndexUpdateType>,
    IsCoreEnum,
    MultiTypeEnum<IsIndexUpdate> {
    Update(1u, IndexUpdate),
    Delete(2u, IndexDelete);

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as IsTypedObjectDataModel<IsIndexUpdate, *, RequestContext, RequestContext> }
    )

    override val alternativeNames: Set<String>? = null
    companion object : MultiTypeEnumDefinition<IndexUpdateType>(
        IndexUpdateType::class, IndexUpdateType::values)
}
