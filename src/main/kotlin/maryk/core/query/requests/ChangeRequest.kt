package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.query.DataObjectChange

/** A Request to change DataObjects for specific DataModel
 * @param dataModel Root model of data to change objects in
 * @param objectChanges Array of object changes
 */
class ChangeRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        vararg val objectChanges: DataObjectChange<DO>
) : AbstractModelRequest<DO, DM>(dataModel) {
    companion object: QueryDataModel<ChangeRequest<*, *>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ChangeRequest(
                        dataModel = it[0] as RootDataModel<Any>
                )
            },
            definitions = listOf(
                    Def(AbstractModelRequest.Properties.dataModel, ChangeRequest<*, *>::dataModel)
            )
    )
}
