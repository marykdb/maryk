package maryk.core.values

import maryk.core.models.AbstractDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.TypedPropertyDefinitions
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.query.RequestContext
import maryk.core.query.changes.IsChange

typealias ValuesImpl = Values<IsValuesPropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class Values<P : IsValuesPropertyDefinitions> internal constructor(
    val propertyDefinitions: P,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, IsValuesDataModel<P>, P>() {
    @Suppress("UNCHECKED_CAST")
    override val dataModel: IsValuesDataModel<P> = propertyDefinitions.Model as IsValuesDataModel<P>

    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: P.() -> List<ValueItem>) =
        Values(
            propertyDefinitions,
            values.copyAdding(pairCreator(this.propertyDefinitions)),
            context
        )

    fun copy(values: IsValueItems) =
        Values(propertyDefinitions, values.copyAdding(values), context)

    fun filterWithSelect(select: IsPropRefGraph<*>?): Values<P> {
        if (select == null) {
            return this
        }

        return Values(
            propertyDefinitions = propertyDefinitions,
            values = this.values.copySelecting(select),
            context = context
        )
    }

    /** Change the Values with given [change] */
    fun change(vararg change: IsChange) = this.change(listOf(*change))

    fun change(changes: List<IsChange>): Values<P> =
        if (changes.isEmpty()) {
            this
        } else {
            val valueItemsToChange = MutableValueItems(mutableListOf())

            for (change in changes) {
                change.changeValues { ref, valueChanger ->
                    valueItemsToChange.copyFromOriginalAndChange(this.values, ref.index, valueChanger)
                }
            }

            Values(propertyDefinitions, values.copyAdding(valueItemsToChange), context)
        }

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Values<*> -> false
        propertyDefinitions != other.propertyDefinitions -> false
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
        (this.dataModel as IsTypedValuesDataModel<*, P>).validate(this)
    }
}

/** Output values to a json string */
fun <V: Values<P>, DO: Any, DM: AbstractDataModel<DO, P, V, *, *>, P: TypedPropertyDefinitions<DM, P>> V.toJson(
    pretty: Boolean = false
): String =
    this.propertyDefinitions.Model.writeJson(this, pretty = pretty)
