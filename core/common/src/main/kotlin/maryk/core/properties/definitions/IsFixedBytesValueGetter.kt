package maryk.core.properties.definitions

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions

/** Interface to get value of fixed bytes encodables */
interface IsFixedBytesValueGetter<out T: Any> {
    /**
     * Get the value from [dataObject] of [DO] with [dataModel] if relevant
     * to be used in a fixed bytes encodable
     */
    fun <DO: Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsObjectDataModel<DO, P>, dataObject: DO): T

    /**
     * Get the value from [values] with [dataModel] of type [DM]
     * to be used in a fixed bytes encodable
     */
    fun <DM: IsValuesDataModel<*>> getValue(dataModel: DM, values: Values<DM, *>): T
}
