package maryk.core.values

import maryk.core.models.IsNamedDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.PropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.Delete
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

    internal fun change(changes: List<IsChange>): Values<DM, P> =
        if (changes.isEmpty()) {
            this
        } else {
            val copiedValues = MutableValueItems(values.toMutableList())
            val mutableReferenceList = mutableListOf<AnyPropertyReference>()

            val valueItemsToChange = MutableValueItems(mutableListOf())

            fun mutableValueCreator(valueToChange: Any?): Any? = when (valueToChange) {
                null -> null
                is List<*> -> valueToChange.toMutableList()
                is Map<*, *> -> valueToChange.toMutableMap()
                is Values<*, *> ->
                    @Suppress("UNCHECKED_CAST")
                    Values(
                        valueToChange.dataModel as IsValuesDataModel<PropertyDefinitions>,
                        MutableValueItems(valueToChange.values.toMutableList()),
                        valueToChange.context
                    )
                is TypedValue<*, *> -> TypedValue(
                    valueToChange.type,
                    mutableValueCreator(valueToChange.value) as Any
                )
                else -> valueToChange
            }

            fun MutableValueItems.change(directReference: PropertyReferenceForValues<*, *, *, *>, valueChanger: (Any?) -> Any?) {
                list.searchItemByIndex(directReference.index).let { index ->
                    when {
                        index < 0 -> {
                            val previousValue = copiedValues.getValueItem(directReference.index)?.value
                            val newValue = mutableValueCreator(previousValue)

                            list.add((index * -1) - 1, ValueItem(directReference.index, valueChanger(newValue) ?: newValue!!))
                        }
                        else -> valueChanger(list[index].value)?.also {
                            list[index] = ValueItem(directReference.index, it)
                        }
                    }
                }
            }

            for (change in changes) {
                when (change) {
                    is Change -> {
                        change.referenceValuePairs.forEach {
                            it.reference.unwrap(mutableReferenceList)

                            var referenceIndex = 0

                            fun valueChanger(value: Any?): Any? {
                                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                                return if (currentRef == null) {
                                    it.value
                                } else {
                                    when (value) {
                                        is Values<*, *> -> (value.values as MutableValueItems).change(currentRef as PropertyReferenceForValues<*, *, *, *>, ::valueChanger)
                                        is MutableList<*> -> when (currentRef) {
                                            is ListItemReference<*, *> -> {
                                                @Suppress("UNCHECKED_CAST")
                                                (value as MutableList<Any>)[currentRef.index.toInt()] = it.value
                                            }
                                            is ListAnyItemReference<*, *> ->
                                                @Suppress("UNCHECKED_CAST")
                                                (value as MutableList<Any>).indices.forEach { index ->
                                                    value[index] = it.value
                                                }
                                            else -> TODO()
                                        }
                                        else -> TODO()
                                    }
                                    null // Deeper change so no overwrite
                                }
                            }

                            when (val ref = mutableReferenceList[referenceIndex++]) {
                                is PropertyReferenceForValues<*, *, *, *> ->
                                    valueItemsToChange.change(ref, ::valueChanger)
                                else -> TODO()
                            }
                        }
                    }
                    is Delete -> TODO()
                    is ListChange -> TODO()
                    is SetChange -> TODO()
                    else -> throw Exception("Unexpected Change to process: $change")
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
