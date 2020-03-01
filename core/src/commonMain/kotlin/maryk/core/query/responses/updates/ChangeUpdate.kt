package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange

/** Update response describing a change to query results with [changes] at [key] in [dataModel] */
data class ChangeUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val dataModel: DM,
    override val key: Key<DM>,
    override val version: ULong,
    val changes: List<IsChange>
) : IsUpdateResponse<DM, P>
