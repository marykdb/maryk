package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.DataModelPropertyContext

/**
 * Creates a DataObjectChange which contains [change] until [lastVersion] for a specific DataObject
 */
fun <DO:Any> Key<DO>.change(
    vararg change: IsChange,
    lastVersion: UInt64? = null
) = DataObjectChange(this, change.toList(), lastVersion)

/**
 * Contains [changes] until [lastVersion] for a specific DataObject by [key]
 */
data class DataObjectChange<out DO: Any> internal constructor(
    val key: Key<DO>,
    val changes: List<IsChange>,
    val lastVersion: UInt64? = null
) {
    internal companion object: QueryDataModel<DataObjectChange<*>>(
        properties = object : PropertyDefinitions<DataObjectChange<*>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.key ?: throw ContextNotFoundException()
                    }
                ), DataObjectChange<*>::key)

                add(1, "changes",
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

                add(2, "lastVersion", NumberDefinition(
                    type = UInt64
                ), DataObjectChange<*>::lastVersion)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = DataObjectChange(
            key = map(0),
            changes = map(1),
            lastVersion = map(2)
        )
    }
}
