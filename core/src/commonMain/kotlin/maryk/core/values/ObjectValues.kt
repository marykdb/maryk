package maryk.core.values

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsStorableDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.query.RequestContext

typealias SimpleObjectValues<DO> = ObjectValues<DO, IsObjectDataModel<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ObjectValues<DO : Any, DM : IsObjectDataModel<DO>> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<DO, DM>() {
    /**
     * Converts values to a strong typed DataObject.
     * Will throw exception if values is missing values for a complete DataObject
     */
    @Suppress("UNCHECKED_CAST")
    fun toDataObject() = (dataModel as IsTypedObjectDataModel<DO, DM, *, *>)(this)

    // ignore context
    override fun equals(other: Any?) = when (other) {
        is ObjectValues<*, *> -> dataModel == other.dataModel && values == other.values
        else -> false
    }

    // ignore context
    override fun hashCode() = 31 * dataModel.hashCode() + values.hashCode()

    override fun toString() = "ObjectValues<${(dataModel as? IsStorableDataModel<*>)?.Meta?.name ?: dataModel}>$values"
}
