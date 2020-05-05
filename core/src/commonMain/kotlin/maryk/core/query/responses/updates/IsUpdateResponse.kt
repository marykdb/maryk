package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.types.Key
import maryk.core.query.changes.Change
import maryk.core.query.changes.ChangeType
import maryk.core.query.changes.Check
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.MultiTypeChange
import maryk.core.query.changes.ObjectCreateModel
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.SetChange

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val type: UpdateResponseType
    val version: ULong
}
