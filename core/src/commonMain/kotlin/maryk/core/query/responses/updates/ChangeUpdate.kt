package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions
import maryk.core.query.responses.statuses.addKey
import maryk.core.query.responses.updates.UpdateResponseType.Change
import maryk.core.values.SimpleObjectValues

/** Update response describing a change to query results with [changes] at [key] */
data class ChangeUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val key: Key<DM>,
    override val version: ULong,
    // The index within the current order
    val index: Int,
    val changes: List<IsChange>
) : IsUpdateResponse<DM, P> {
    override val type = Change

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<ChangeUpdate<*, *>>() {
        val key by addKey(ChangeUpdate<*, *>::key)
        val version by number(2u, getter = ChangeUpdate<*, *>::version, type = UInt64)
        val index by number(3u, getter = ChangeUpdate<*, *>::index, type = SInt32)
        val changes by list(
            index = 4u,
            getter = ChangeUpdate<*, *>::changes,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = ChangeType,
                definitionMap = mapOfChangeDefinitions
            ),
            toSerializable = { TypedValue(it.changeType, it) },
            fromSerializable = { it.value }
        )
    }

    internal companion object : SimpleQueryDataModel<ChangeUpdate<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<ChangeUpdate<*, *>>) = ChangeUpdate<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            key = values(1u),
            version = values(2u),
            index = values(3u),
            changes = values(4u)
        )
    }
}
