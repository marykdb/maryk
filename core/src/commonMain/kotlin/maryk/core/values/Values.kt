package maryk.core.values

import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.RequestContext

typealias ValuesImpl = Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM : IsValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM, P>() {
    fun copy(pairCreator: P.() -> Array<ValueItem>) =
        Values(
            dataModel,
            values.copyAdding(pairCreator(this.dataModel.properties)),
            context
        )

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Values<*, *> -> false
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
        return "Values<$modelName>$values"
    }

    /**
     * Validates the contents of values
     */
    fun validate() {
        @Suppress("UNCHECKED_CAST")
        (this.dataModel as IsTypedValuesDataModel<DM, P>).validate(this)
    }
}
