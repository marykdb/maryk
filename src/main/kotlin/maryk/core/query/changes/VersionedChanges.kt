package maryk.core.query.changes

import maryk.core.objects.Def
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
    object Properties : PropertyDefinitions<VersionedChanges>() {
        val version = NumberDefinition(
                name = "version",
                index = 0,
                type = UInt64
        )
        val changes = ListDefinition(
                name = "changes",
                index = 1,
                required = true,
                valueDefinition = MultiTypeDefinition(
                        required = true,
                        getDefinition = mapOfChangeDefinitions::get
                )
        )
    }

    companion object: QueryDataModel<VersionedChanges>(
            definitions = listOf(
                    Def(Properties.version, VersionedChanges::version),
                    Def(Properties.changes, { it.changes.map { TypedValue(it.changeType.index, it) } })
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = VersionedChanges(
                version = map[0] as UInt64,
                changes = (map[1] as List<TypedValue<IsChange>>?)?.map { it.value } ?: emptyList()
        )
    }
}