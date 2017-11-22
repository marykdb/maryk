package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32

/** Defines a Scan from key request. */
interface IsScanRequest<DO: Any, out DM: RootDataModel<DO>> : IsFetchRequest<DO, DM> {
    val startKey: Key<DO>
    val limit: UInt32
}