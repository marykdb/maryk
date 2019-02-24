package maryk.core.values

import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.RequestContext

typealias SimpleObjectValues<DO> = ObjectValues<DO, ObjectPropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ObjectValues<DO : Any, P : ObjectPropertyDefinitions<DO>> internal constructor(
    override val dataModel: IsObjectDataModel<DO, P>,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<DO, IsObjectDataModel<DO, P>, P>() {
    /**
     * Converts values to a strong typed DataObject.
     * Will throw exception if values is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this)

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
        val modelName = (dataModel as? IsNamedDataModel<*>)?.name ?: dataModel
        return "ObjectValues<$modelName>$values"
    }
}
