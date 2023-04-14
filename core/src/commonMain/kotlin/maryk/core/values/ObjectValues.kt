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
    fun toDataObject() = (this.dataModel as IsTypedObjectDataModel<DO, DM, *, *>).invoke(this)

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is ObjectValues<*, *> -> false
        dataModel != other.dataModel -> false
        values != other.values -> false
        else -> true
    }

    // ignore context
    override fun hashCode(): Int {
        var result = dataModel.hashCode()
        result = 31 * result + values.hashCode()
        return result
    }

    override fun toString(): String {
        val modelName = (dataModel as? IsStorableDataModel<*>)?.Meta?.name ?: dataModel
        return "ObjectValues<$modelName>$values"
    }
}
