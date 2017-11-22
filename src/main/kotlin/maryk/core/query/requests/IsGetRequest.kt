package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.types.Key

/** Defines a Get by keys request. */
interface IsGetRequest<DO: Any, out DM: RootDataModel<DO>> : IsFetchRequest<DO, DM> {
    val keys: List<Key<DO>>
}