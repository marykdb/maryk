package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64

/** Contains a list of changes that belongs to a version
 * @param version of which changes belong to
 * @param changes which are changed at this version
 */
data class VersionedChanges(
        val version: UInt64,
        val changes: List<IsChange>
) {
    companion object: QueryDataModel<VersionedChanges>(
            properties = object : PropertyDefinitions<VersionedChanges>() {
                init {
                    add(0, "version", NumberDefinition(
                            type = UInt64
                    ), VersionedChanges::version)

                    add(1, "changes", ListDefinition(
                            valueDefinition = MultiTypeDefinition(
                                    definitionMap = mapOfChangeDefinitions
                            )
                    )) {
                        it.changes.map { TypedValue(it.changeType.index, it) }
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = VersionedChanges(
                version = map[0] as UInt64,
                changes = (map[1] as List<TypedValue<IsChange>>?)?.map { it.value } ?: emptyList()
        )
    }
}