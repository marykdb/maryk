package maryk.core.query.changes

import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues

/** Contains a list of [changes] that belongs to a [version] */
data class VersionedChanges(
    val version: ULong,
    val changes: List<IsChange>
) {
    override fun toString() = "VersionedChanges($version)[${changes.joinToString()}]"

    object Properties : ObjectPropertyDefinitions<VersionedChanges>() {
        val version = add(
            1u, "version",
            NumberDefinition(
                type = UInt64
            ),
            VersionedChanges::version
        )

        val changes = add(
            2u, "changes",
            ListDefinition(
                default = emptyList(),
                valueDefinition = MultiTypeDefinition(
                    typeEnum = ChangeType,
                    definitionMap = mapOfChangeDefinitions
                )
            ),
            getter = VersionedChanges::changes,
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value }
        )
    }

    companion object : QueryDataModel<VersionedChanges, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<VersionedChanges, Properties>) = VersionedChanges(
            version = values(1u),
            changes = values(2u)
        )
    }
}
