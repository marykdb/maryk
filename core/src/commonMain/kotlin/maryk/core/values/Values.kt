package maryk.core.values

import maryk.core.models.AbstractDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.TypedPropertyDefinitions
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.validate
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange

typealias ValuesImpl = Values<IsValuesPropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class Values<DM : IsValuesPropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM>() {
    @Suppress("UNCHECKED_CAST")

    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: DM.() -> List<ValueItem>) =
        Values(
            dataModel,
            values.copyAdding(pairCreator(dataModel)),
            context
        )

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
    fun change(vararg change: IsChange) = this.change(listOf(*change))

    fun change(changes: List<IsChange>): Values<DM> =
        if (changes.isEmpty()) {
            this
        } else {
            val valueItemsToChange = MutableValueItems(mutableListOf())

            for (change in changes) {
                change.changeValues { ref, valueChanger ->
                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index, valueChanger)
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
    override fun hashCode(): Int {
        var result = dataModel.Model.hashCode()
        result = 31 * result + values.hashCode()
        return result
    }

    override fun toString(): String {
        val modelName = (dataModel.Model as? IsNamedDataModel<*>)?.name ?: dataModel
        return "Values<$modelName>${values.toString(dataModel)}"
    }

    /**
     * Validates the contents of values
     */
    fun validate() {
        this.dataModel.validate(this)
    }
}

/** Output values to a json string */
fun <V: Values<P>, DO: Any, DM: AbstractDataModel<DO, P, V, *, *>, P: TypedPropertyDefinitions<DM, P>> V.toJson(
    pretty: Boolean = false
): String =
    this.dataModel.Model.writeJson(this, pretty = pretty)
