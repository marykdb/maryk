package maryk.core.properties.definitions

import maryk.core.models.IsDataModel
import maryk.core.properties.ObjectPropertyDefinitions

/** Interface to get value of fixed bytes encodables */
interface IsFixedBytesValueGetter<out T: Any> {
    /** Get the value from [dataObject] of [DO] with [dataModel] if relevant
     * to be used in a fixed bytes encodable
     */
    fun <DO: Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsDataModel<DO, P>, dataObject: DO): T
}
