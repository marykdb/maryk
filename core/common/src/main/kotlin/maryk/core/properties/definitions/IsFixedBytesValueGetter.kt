package maryk.core.properties.definitions

import maryk.core.objects.IsDataModel

/** Interface to get value of fixed bytes encodables */
interface IsFixedBytesValueGetter<out T: Any> {
    /** Get the value from [dataObject] of [DO] with [dataModel] if relevant
     * to be used in a fixed bytes encodable
     */
    fun <DO: Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO): T
}
