package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.DataModelPropertyContext

/**
 * Creates a DataObjectChange which contains [change] until [lastVersion] for a specific DataObject
 */
fun <DM: IsRootDataModel<*>> Key<DM>.change(
    vararg change: IsChange,
    lastVersion: UInt64? = null
) = DataObjectChange(this, change.toList(), lastVersion)

/**
 * Contains [changes] until [lastVersion] for a specific DataObject by [key]
 */
data class DataObjectChange<out DM: IsRootDataModel<*>> internal constructor(
    val key: Key<DM>,
    val changes: List<IsChange>,
    val lastVersion: UInt64? = null
) {
    internal companion object: SimpleQueryDataModel<DataObjectChange<*>>(
        properties = object : ObjectPropertyDefinitions<DataObjectChange<*>>() {
            init {
                add(1, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                ), DataObjectChange<*>::key)

                add(2, "changes",
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

                add(3, "lastVersion", NumberDefinition(
                    type = UInt64
                ), DataObjectChange<*>::lastVersion)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<DataObjectChange<*>>) = DataObjectChange(
            key = map(1),
            changes = map(2),
            lastVersion = map(3)
        )
    }
}
