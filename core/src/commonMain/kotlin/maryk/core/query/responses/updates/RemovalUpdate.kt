package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.number
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.statuses.addKey
import maryk.core.query.responses.updates.UpdateResponseType.Removal
import maryk.core.values.SimpleObjectValues

/** Indexed type of update responses */
enum class RemovalReason(override val index: UInt, override val alternativeNames: Set<String>? = null) : IndexedEnumComparable<RemovalReason>, IsCoreEnum {
    NotInRange(1u),
    SoftDelete(2u),
    HardDelete(3u);

    companion object : IndexedEnumDefinition<RemovalReason>(RemovalReason::class, { entries })
}

/** Update response describing a removal from query result at [key] for [reason] */
data class RemovalUpdate<DM: IsRootDataModel>(
    val key: Key<DM>,
    override val version: ULong,
    val reason: RemovalReason
) : IsUpdateResponse<DM> {
    override val type = Removal

    internal companion object : SimpleQueryModel<RemovalUpdate<*>>() {
        val key by addKey(RemovalUpdate<*>::key)
        val version by number(2u, getter = RemovalUpdate<*>::version, type = UInt64)
        val reason by enum(3u, getter = RemovalUpdate<*>::reason, enum = RemovalReason)

        override fun invoke(values: SimpleObjectValues<RemovalUpdate<*>>) = RemovalUpdate<IsRootDataModel>(
            key = values(1u),
            version = values(2u),
            reason = values(3u)
        )
    }
}
