package maryk.core.query.changes

import maryk.core.models.QueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.ObjectValues

/** Contains a list of [changes] that belongs to a [version] */
data class VersionedChanges(
    val version: ULong,
    val changes: List<IsChange>
) {
    override fun toString() = "VersionedChanges($version)[${changes.joinToString()}]"

    companion object : QueryModel<VersionedChanges, Companion>() {
        val version by number(
            1u,
            VersionedChanges::version,
            UInt64
        )

        val changes by list(
            index = 2u,
            getter = VersionedChanges::changes,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = ChangeType,
                definitionMap = mapOfChangeDefinitions
            ),
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: ObjectValues<VersionedChanges, Companion>) = VersionedChanges(
            version = values(1u),
            changes = values(2u)
        )
    }
}
