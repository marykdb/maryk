package maryk.datastore.shared

import maryk.core.exceptions.RequestException

/** Reject update versions which cannot leave room for observation and a following mutation. */
fun requireSafeUpdateVersion(version: ULong) {
    if (version >= ULong.MAX_VALUE - 1uL) {
        throw RequestException("Update version $version is too high to safely advance the store clock")
    }
}
