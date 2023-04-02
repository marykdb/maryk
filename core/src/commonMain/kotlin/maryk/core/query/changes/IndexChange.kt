package maryk.core.query.changes

import maryk.core.models.IsRootDataModel
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues

/**
 * Describes [changes] to index inside data stores
 * Useful for reordering results in on index ordered values
 */
data class IndexChange(
    val changes: List<IsIndexUpdate>
): IsChange {
    override val changeType = ChangeType.IndexChange

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootDataModel>) = this

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        // Do nothing since it cannot operate on object itself
    }

    @Suppress("UNCHECKED_CAST")
    companion object : SingleTypedValueDataModel<List<TypedValue<IndexUpdateType, IsIndexUpdate>>, IndexChange, Companion, ContainsDefinitionsContext>(
        singlePropertyDefinitionGetter = { Companion.changes as IsDefinitionWrapper<List<TypedValue<IndexUpdateType, IsIndexUpdate>>, out List<TypedValue<IndexUpdateType, IsIndexUpdate>>, ContainsDefinitionsContext, IndexChange> }
    ) {
        val changes by list(
            index = 1u,
            getter = IndexChange::changes,
            valueDefinition = MultiTypeDefinition(
                required = true,
                final = true,
                typeEnum = IndexUpdateType
            ),
            toSerializable = { TypedValue(it.type, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: ObjectValues<IndexChange, Companion>) =
            IndexChange(
                changes = values(1u)
            )
    }
}
