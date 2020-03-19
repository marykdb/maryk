package maryk.core.values

import maryk.core.exceptions.RequestException
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
import maryk.core.query.changes.IncMapAddition
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange

typealias ValuesImpl = Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM : IsValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM, P>() {
    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: P.() -> List<ValueItem>) =
        Values(
            dataModel,
            values.copyAdding(pairCreator(this.dataModel.properties)),
            context
        )

    fun copy(values: IsValueItems) =
        Values(dataModel, values.copyAdding(values), context)

    fun filterWithSelect(select: IsPropRefGraph<*>?): Values<DM, P> {
        if (select == null) {
            return this
        }

        return Values(
            dataModel = dataModel,
            values = this.values.copySelecting(select),
            context = context
        )
    }

    /** Change the Values with given [change] */
    fun change(vararg change: IsChange) = this.change(listOf(*change))

    fun change(changes: List<IsChange>): Values<DM, P> =
        if (changes.isEmpty()) {
            this
        } else {
            val valueItemsToChange = MutableValueItems(mutableListOf())

            for (change in changes) {
                change.changeValues { ref, valueChanger ->
                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index, valueChanger)
                }
            }

            Values(dataModel, values.copyAdding(valueItemsToChange.list), context)
        }

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
        return "Values<$modelName>${values.toString(dataModel)}"
    }

    /**
     * Validates the contents of values
     */
    fun validate() {
        @Suppress("UNCHECKED_CAST")
        (this.dataModel as IsTypedValuesDataModel<DM, P>).validate(this)
    }
}
