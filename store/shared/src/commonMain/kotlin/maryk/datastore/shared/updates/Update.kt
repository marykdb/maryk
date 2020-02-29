package maryk.datastore.shared.updates

import maryk.core.clock.HLC
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange

/** Describes an update on an object with [key] in [dataModel] with [version] */
sealed class Update<DM: IsRootValuesDataModel<*>>(
    val dataModel: DM,
    val key: Key<DM>,
    val version: HLC
) {
    class Addition<DM: IsRootValuesDataModel<*>>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC
    ): Update<DM>(dataModel, key, version)

    class Deletion<DM: IsRootValuesDataModel<*>>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC,
        val isHardDelete: Boolean
    ): Update<DM>(dataModel, key, version)

    class Change<DM: IsRootValuesDataModel<*>>(
        dataModel: DM,
        key: Key<DM>,
        version: HLC,
        val changes: List<IsChange>
    ): Update<DM>(dataModel, key, version)
}
