package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.IsDataModel

/** Interface to get value of fixed bytes encodables */
interface IsFixedBytesValueGetter<out T: Any> {
    /** Get the value to be used in a key
     * @param dataModel to use to fetch property if relevant
     * @param dataObject to get property from
     */
    fun <DO: Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO): T

}
