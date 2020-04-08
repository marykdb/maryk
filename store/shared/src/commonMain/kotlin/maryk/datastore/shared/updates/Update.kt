package maryk.datastore.shared.updates

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.values.Values

/** Describes an update on an object with [key] in [dataModel] with [version] */
sealed class Update<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val dataModel: DM,
    val key: Key<DM>,
    val version: HLC
) {
    class Addition<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC,
        val values: Values<DM, P>
    ): Update<DM, P>(dataModel, key, version)

    class Deletion<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC,
        val isHardDelete: Boolean
    ): Update<DM, P>(dataModel, key, version)

    class Change<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC,
        val changes: List<IsChange>,
        val indexUpdates: List<IsIndexUpdate>?
    ): Update<DM, P>(dataModel, key, version)
}
