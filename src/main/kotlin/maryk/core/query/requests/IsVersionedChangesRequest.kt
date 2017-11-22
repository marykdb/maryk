package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.types.numeric.UInt32

/** Request for all versioned changes from a version and later */
interface IsVersionedChangesRequest<DO: Any, out DM: RootDataModel<DO>> : IsChangesRequest<DO, DM> {
    val maxVersions: UInt32
}