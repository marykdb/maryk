package maryk.core.objects

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.RequestContext

typealias SimpleObjectValues<DO> = ObjectValues<DO, ObjectPropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ObjectValues<DO: Any, P: ObjectPropertyDefinitions<DO>> internal constructor(
    override val dataModel: IsObjectDataModel<DO, P>,
    override val map: Map<Int, Any?>,
    override val context: RequestContext? = null
): AbstractValues<DO, IsObjectDataModel<DO, P>, P>() {
    /**
     * Converts map to a strong typed DataObject.
     * Will throw exception if map is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this)

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is ObjectValues<*, *> -> false
        dataModel != other.dataModel -> false
        map != other.map -> false
        else -> true
    }

    // ignore context
    override fun hashCode(): Int {
        var result = dataModel.hashCode()
        result = 31 * result + map.hashCode()
        return result
    }
}
