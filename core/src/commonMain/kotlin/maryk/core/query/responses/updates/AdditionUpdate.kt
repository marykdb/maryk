package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.values.Values

/** Update response describing an addition to query result of [values] at [key] */
data class AdditionUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val key: Key<DM>,
    override val version: ULong,
    val insertionIndex: Int,
    val values: Values<DM, P>
) : IsUpdateResponse<DM, P>
