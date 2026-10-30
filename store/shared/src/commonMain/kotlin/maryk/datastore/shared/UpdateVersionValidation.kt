package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.properties.types.Key
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.InitialChangesUpdate
import maryk.core.query.responses.updates.IsUpdateResponse

/** Reject update versions which cannot leave room for observation and a following mutation. */
fun requireSafeUpdateVersion(version: ULong) {
    if (version >= ULong.MAX_VALUE - 1uL) {
        throw RequestException("Update version $version is too high to safely advance the store clock")
    }
}

/** Reject unsafe versions carried by an update envelope or its replay events. */
fun requireSafeUpdateVersion(update: IsUpdateResponse<*>) {
    requireSafeUpdateVersion(update.version)
    when (update) {
        is AdditionUpdate<*> -> requireSafeUpdateVersion(update.firstVersion)
        is InitialChangesUpdate<*> -> {
            val lastVersionByKey = mutableMapOf<Key<*>, ULong>()
            update.changes.forEach { objectChanges ->
                objectChanges.changes.forEach { versionedChanges ->
                    val version = versionedChanges.version
                    requireSafeUpdateVersion(version)
                    if (version > update.version) {
                        throw RequestException("Initial change version $version exceeds update version ${update.version}")
                    }
                    val previousVersion = lastVersionByKey.put(objectChanges.key, version)
                    if (previousVersion != null && version <= previousVersion) {
                        throw RequestException("Initial change versions for a record must be strictly increasing")
                    }
                }
            }
        }
    }
}
