package maryk.core.query.changes

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

/** Contains changes for a specific DataObject by key
 * @param key of DataObject to change
 * @param changes for DataObject
 * @param lastVersion of data which is contained
 */
data class DataObjectChange<out DO: Any>(
        val key: Key<DO>,
        val changes: List<IsChange>,
        val lastVersion: UInt64? = null
) {
    constructor(
            key: Key<DO>,
            vararg change: IsChange,
            lastVersion: UInt64? = null
    ) : this(key, change.toList(), lastVersion)

    companion object: QueryDataModel<DataObjectChange<*>>(
            properties = object : PropertyDefinitions<DataObjectChange<*>>() {
                init {
                    add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!!.key }
                    ), DataObjectChange<*>::key)

                    add(1, "changes", ListDefinition(
                            valueDefinition = MultiTypeDefinition(
                                    definitionMap = mapOfChangeDefinitions
                            )
                    )) {
                        it.changes.map { TypedValue(it.changeType, it) }
                    }

                    add(2, "lastVersion", NumberDefinition(
                            type = UInt64
                    ), DataObjectChange<*>::lastVersion)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DataObjectChange(
                key = map[0] as Key<Any>,
                changes = (map[1] as List<TypedValue<ChangeType, IsChange>>?)?.map { it.value } ?: emptyList(),
                lastVersion = map[2] as UInt64?
        )
    }
}
