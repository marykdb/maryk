package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64

/** Contains a list of [changes] that belongs to a [version] */
data class VersionedChanges(
    val version: UInt64,
    val changes: List<IsChange>
) {
    internal companion object: QueryDataModel<VersionedChanges>(
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
                    it.changes.map { TypedValue(it.changeType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = VersionedChanges(
            version = map(0),
            changes = map<List<TypedValue<ChangeType, IsChange>>?>(1)?.map { it.value } ?: emptyList()
        )
    }
}
