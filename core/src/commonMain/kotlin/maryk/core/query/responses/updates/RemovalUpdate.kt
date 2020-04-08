package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key

enum class RemovalReason {
    SoftDelete, HardDelete, NotInRange
}

/** Update response describing a removal from query result at [key] */
data class RemovalUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val key: Key<DM>,
    override val version: ULong,
    val reason: RemovalReason
) : IsUpdateResponse<DM, P>
