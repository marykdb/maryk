package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions

/** Successful add of given object with [key], [version] and added [changes] */
data class AddSuccess<DO: Any>(
    val key: Key<DO>,
    val version: UInt64,
    val changes: List<IsChange>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ADD_SUCCESS

    internal companion object: QueryDataModel<AddSuccess<*>>(
        properties = object : PropertyDefinitions<AddSuccess<*>>(){
            init {
                IsResponseStatus.addKey(this, AddSuccess<*>::key)
                add(1,"version", NumberDefinition(type = UInt64), AddSuccess<*>::version)
                add(2,"changes", ListDefinition(
                    valueDefinition = MultiTypeDefinition(
                        definitionMap = mapOfChangeDefinitions
                    )
                )) { it.changes.map { TypedValue(it.changeType, it) } }
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = AddSuccess(
            key = map[0] as Key<Any>,
            version = map[1] as UInt64,
            changes = (map[2] as List<TypedValue<ChangeType, IsChange>>?)?.map { it.value } ?: emptyList()
        )
    }
}