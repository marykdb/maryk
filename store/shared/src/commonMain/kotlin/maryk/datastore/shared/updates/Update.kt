package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.values.Values

/** Describes an update on an object with [key] in [dataModel] with [version] */
sealed class Update<DM: IsRootDataModel>(
    val dataModel: DM,
    val key: Key<DM>,
    val version: ULong
) : IsUpdateAction {
    class Addition<DM: IsRootDataModel>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val values: Values<DM>
    ): Update<DM>(dataModel, key, version)

    class Deletion<DM: IsRootDataModel>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val isHardDelete: Boolean
    ): Update<DM>(dataModel, key, version)

    class Change<DM: IsRootDataModel>(
        dataModel: DM,
        key: Key<DM>,
        version: ULong,
        val changes: List<IsChange>
    ): Update<DM>(dataModel, key, version)
}
