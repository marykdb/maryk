package maryk.core.query.changes

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.ValueMap
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
    internal companion object: SimpleQueryDataModel<VersionedChanges>(
        properties = object : PropertyDefinitions<VersionedChanges>() {
            init {
                add(0, "version", NumberDefinition(
                    type = UInt64
                ), VersionedChanges::version)

                add(1, "changes",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = MultiTypeDefinition(
                            typeEnum = ChangeType,
                            definitionMap = mapOfChangeDefinitions
                        )
                    ),
                    getter = VersionedChanges::changes,
                    toSerializable = { TypedValue(it.changeType, it) },
                    fromSerializable = { it.value as IsChange }
                )
            }
        }
    ) {
        override fun invoke(map: ValueMap<VersionedChanges>) = VersionedChanges(
            version = map(0),
            changes = map(1)
        )
    }
}
