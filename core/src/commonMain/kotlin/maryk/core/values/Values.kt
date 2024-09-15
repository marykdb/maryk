package maryk.core.values

import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.validate
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange

typealias ValuesImpl = Values<IsValuesDataModel>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class Values<DM : IsValuesDataModel> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM>() {
    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: DM.() -> List<ValueItem>) =
        Values(dataModel, values.copyAdding(pairCreator(dataModel)), context)

    fun copy(values: IsValueItems) =
        Values(dataModel, values.copyAdding(values), context)

    fun filterWithSelect(select: IsPropRefGraph<*>?): Values<DM> {
        if (select == null) {
            return this
        }

        return Values(
            dataModel = dataModel,
            values = this.values.copySelecting(select),
            context = context,
        )
    }

    /** Change the Values with given [change] */
    fun change(vararg change: IsChange) = change(change.toList())

    fun change(changes: List<IsChange>): Values<DM> =
        if (changes.isEmpty()) this
        else {
            val valueItemsToChange = MutableValueItems(mutableListOf())
            changes.forEach { change ->
                change.changeValues { ref, valueChanger ->
                    valueItemsToChange.copyFromOriginalAndChange(values, ref.index, valueChanger)
                }
            }
            Values(dataModel, values.copyAdding(valueItemsToChange), context)
        }

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Values<*> -> false
        dataModel != other.dataModel -> false
        values != other.values -> false
        else -> true
    }

    // ignore context
    override fun hashCode() = 31 * dataModel.Meta.hashCode() + values.hashCode()

    override fun toString() = "Values<${dataModel.Meta.name}>${values.toString(dataModel)}"

    /**
     * Validates the contents of values
     */
    fun validate() = dataModel.validate(this)
}

/** Output values to a json string */
fun <V: Values<DM>, DM: TypedValuesDataModel<DM>> V.toJson(pretty: Boolean = false) =
    dataModel.Serializer.writeJson(this, pretty = pretty)
