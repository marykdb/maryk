@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.values.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext

/**
 * Creates a DataObjectChange which contains [change] until [lastVersion] for a specific DataObject
 */
fun <DM: IsRootDataModel<*>> Key<DM>.change(
    vararg change: IsChange,
    lastVersion: ULong? = null
) = DataObjectChange(this, change.toList(), lastVersion)

/**
 * Contains [changes] until [lastVersion] for a specific DataObject by [key]
 */
data class DataObjectChange<out DM: IsRootDataModel<*>> internal constructor(
    val key: Key<DM>,
    val changes: List<IsChange>,
    val lastVersion: ULong? = null
) {
    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<DataObjectChange<*>>() {
        val key = add(1, "key", ContextualReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
            }
        ), DataObjectChange<*>::key)

        val changes = add(2, "changes",
            ListDefinition(
                default = emptyList(),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = ChangeType,
                    definitionMap = mapOfChangeDefinitions
                )
            ),
            getter = DataObjectChange<*>::changes,
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value as IsChange }
        )

        val lastVersion = add(3, "lastVersion", NumberDefinition(
            type = UInt64
        ), DataObjectChange<*>::lastVersion)
    }

    companion object: QueryDataModel<DataObjectChange<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<DataObjectChange<*>, Properties>) = DataObjectChange(
            key = map(1),
            changes = map(2),
            lastVersion = map(3)
        )
    }
}
