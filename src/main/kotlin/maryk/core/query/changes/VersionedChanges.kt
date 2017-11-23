package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
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
    object Properties {
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
            construct = {
                @Suppress("UNCHECKED_CAST")
                VersionedChanges(
                        version = it[0] as UInt64,
                        changes = (it[1] as List<TypedValue<IsChange>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(Properties.version, VersionedChanges::version),
                    Def(Properties.changes, { it.changes.map { TypedValue(it.changeType.index, it) } })
            )
    )
}