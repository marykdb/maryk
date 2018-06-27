package maryk.core.objects

import maryk.core.models.IsDataModel

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
class DataObjectMap<DO: Any>(
    val dataModel: IsDataModel<DO>,
    val map: Map<Int, Any?>
) : Map<Int, Any?> by map {
    /**
     * Converts map to a strong typed DataObject.
     * Will throw exception if map is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this.map)
}
