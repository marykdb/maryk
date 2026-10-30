package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.values.Values
import maryk.core.values.copyWithOwnedValues

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
        values: Values<DM>,
        val isDeleted: Boolean,
    ): Update<DM>(dataModel, key, version) {
        val values = values.copyWithOwnedValues()

        constructor(
            dataModel: DM,
            key: Key<DM>,
            version: ULong,
            values: Values<DM>,
        ) : this(dataModel, key, version, values, false)
    }

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
        changes: List<IsChange>
    ): Update<DM>(dataModel, key, version) {
        val changes = changes.map { it.copyWithOwnedValues() }
    }
}
