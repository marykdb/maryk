package maryk.datastore.shared.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.values.Values

/** Describes an update on an object with [key] in [dataModel] with [version] */
sealed class Update<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val dataModel: DM,
    val key: Key<DM>,
    val version: ULong
) : IsUpdateAction {
    class Addition<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val values: Values<DM, P>
    ): Update<DM, P>(dataModel, key, version)

    class Deletion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val isHardDelete: Boolean
    ): Update<DM, P>(dataModel, key, version)

    class Change<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val changes: List<IsChange>
    ): Update<DM, P>(dataModel, key, version)
}
