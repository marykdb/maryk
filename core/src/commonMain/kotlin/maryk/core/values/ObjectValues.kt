package maryk.core.values

import maryk.core.properties.IsStorableModel
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsTypedObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.RequestContext

typealias SimpleObjectValues<DO> = ObjectValues<DO, ObjectPropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ObjectValues<DO : Any, DM : IsObjectPropertyDefinitions<DO>> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<DO, DM>() {
    /**
     * Converts values to a strong typed DataObject.
     * Will throw exception if values is missing values for a complete DataObject
     */
    @Suppress("UNCHECKED_CAST")
    fun toDataObject() = (this.dataModel as IsTypedObjectPropertyDefinitions<DO, DM, *>).invoke(this)

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
        val modelName = (dataModel as? IsStorableModel)?.Model?.name ?: dataModel
        return "ObjectValues<$modelName>$values"
    }
}
