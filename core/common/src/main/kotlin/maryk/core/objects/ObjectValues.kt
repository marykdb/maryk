package maryk.core.objects

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions

typealias SimpleObjectValues<DO> = ObjectValues<DO, ObjectPropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ObjectValues<DO: Any, P: ObjectPropertyDefinitions<DO>> internal constructor(
    override val dataModel: IsObjectDataModel<DO, P>,
    override val map: Map<Int, Any?>
): AbstractValues<DO, IsObjectDataModel<DO, P>, P>() {
    /**
     * Converts map to a strong typed DataObject.
     * Will throw exception if map is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this)
}
