package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val type: UpdateResponseType
    val version: ULong
}
