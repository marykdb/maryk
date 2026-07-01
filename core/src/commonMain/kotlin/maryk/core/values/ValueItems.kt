
package maryk.core.values

import maryk.core.models.IsDataModel
import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.PropRefGraph
import maryk.core.properties.types.MutableTypedValue
import maryk.core.properties.types.TypedValue
import kotlin.jvm.JvmInline

interface IsValueItems : Iterable<ValueItem> {
    val size: Int
    operator fun get(index: UInt): Any?
    fun getValueItem(index: UInt): ValueItem?
    fun contains(index: UInt): Boolean
    fun copyAdding(toAdd: Iterable<ValueItem>): IsValueItems
    fun copySelecting(select: IsPropRefGraph<*>): IsValueItems
    fun toString(dataModel: IsDataModel): String
}

interface IsValueItemsImpl : IsValueItems {
    val list: List<ValueItem>

    override val size get() = list.size
    override fun contains(index: UInt) = list.binarySearch { it.index compareTo index } >= 0
    override operator fun get(index: UInt) = list.getOrNull(list.searchItemByIndex(index))?.value
    override fun getValueItem(index: UInt) = list.getOrNull(list.searchItemByIndex(index))
    override fun iterator() = this.list.iterator()

    override fun copyAdding(toAdd: Iterable<ValueItem>) = MutableValueItems(ArrayList(list)).also { items ->
        for (addition in toAdd) {
            items += addition
        }
    }

    override fun copySelecting(select: IsPropRefGraph<*>) = MutableValueItems(
        list.mapNotNullTo(ArrayList()) { valueItem ->
            when {
                !select.contains(valueItem.index) -> null
                valueItem.value is Values<*> -> {
                    (select.selectNodeOrNull(valueItem.index) as? PropRefGraph<*, *>)?.let { subSelect ->
                        ValueItem(valueItem.index, valueItem.value.filterWithSelect(subSelect))
                    } ?: valueItem
                }
                else -> valueItem
            }
        }
    )

    override fun toString(dataModel: IsDataModel) = list.joinToString(
        separator = ", ",
        prefix = "{",
        postfix = "}"
    ) { "${dataModel[it.index]?.name ?: it.index}=${it.value}" }
}

internal fun List<ValueItem>.searchItemByIndex(index: UInt) =
    // Index can never be at a higher spot in list than index itself
    binarySearch(toIndex = minOf(index.toIntOrListSize(size), size)) { it.index compareTo index }

private fun UInt.toIntOrListSize(size: Int): Int =
    if (this > Int.MAX_VALUE.toUInt()) size else toInt()

val EmptyValueItems: IsValueItems = MutableValueItems()
@Suppress("FunctionName")
fun ValueItems(vararg item: ValueItem): IsValueItems = MutableValueItems(*item)

@JvmInline
value class MutableValueItems(
    override val list: MutableList<ValueItem>
) : IsValueItemsImpl {
    override val size get() = list.size

    constructor() : this(mutableListOf())

    constructor(vararg item: ValueItem) : this(mutableListOf(*item))

    /**
     * Adds ValueItem to ValueItems.
     * If ValueItem contains Values object then it is merging it with existing data.
     */
    operator fun plusAssign(valueItem: ValueItem) {
        val index = list.searchItemByIndex(valueItem.index)
        when {
            index < 0 -> if (valueItem.value != Unit) {
                list.add(-index - 1, valueItem)
            } else Unit // Is deleted so do nothing
            else -> when {
                valueItem.value is Unit || (valueItem.value as? TypedValue<*, *>)?.value is Unit -> list.removeAt(index)
                else -> list[index] = when (val value = valueItem.value) {
                    is Values<*> -> ValueItem(valueItem.index, (list[index].value as Values<*>).copy(value.values))
                    else -> valueItem
                }
            }
        }
    }

    operator fun set(index: UInt, value: Any) {
        val searchIndex = list.searchItemByIndex(index)
        val valueItem = ValueItem(index, value)
        if (searchIndex < 0) list.add(-searchIndex - 1, valueItem) else list[searchIndex] = valueItem
    }

    fun remove(index: UInt) = this.list.searchItemByIndex(index).let {
        when {
            it < 0 -> null
            else -> list.removeAt(it)
        }
    }

    /**
     * Changes valueItem at [referenceIndex] with [valueChanger]
     * If [referenceIndex] is not yet contained in this valueItems then fetch it from [sourceValueItems]
     */
    fun copyFromOriginalAndChange(
        sourceValueItems: IsValueItems?,
        referenceIndex: UInt,
        valueChanger: (Any?, Any?) -> Any?
    ) {
        val index = list.searchItemByIndex(referenceIndex)
        val originalValue = sourceValueItems?.getValueItem(referenceIndex)?.value
        when {
            index < 0 -> {
                val newValue = mutableValueCreator(originalValue)
                when (val changedValue = valueChanger(originalValue, newValue)) {
                    Unit -> list.add(-index - 1, ValueItem(referenceIndex, Unit))
                    null -> newValue?.let { list.add(-index - 1, ValueItem(referenceIndex, it)) }
                    else -> list.add(-index - 1, ValueItem(referenceIndex, changedValue))
                }
            }
            else -> when (val changedValue = valueChanger(originalValue, list[index].value)) {
                Unit -> list.removeAt(index)
                null -> Unit
                else -> list[index] = ValueItem(referenceIndex, changedValue)
            }
        }
    }

    fun fillWithPairs(dataModel: IsTypedDataModel<*>, pairs: Array<out ValueItem?>, setDefaults: Boolean) {
        pairs.filterNotNull().forEach { this += it }
        if (setDefaults) {
            setWithDefaults(dataModel)
        }
    }

    fun fillWithPairs(dataModel: IsTypedDataModel<*>, pairs: IsValueItems, setDefaults: Boolean) {
        pairs.forEach { this += it }
        if (setDefaults) {
            setWithDefaults(dataModel)
        }
    }

    private fun setWithDefaults(dataModel: IsTypedDataModel<*>) {
        dataModel.allWithDefaults
            .filter { this[it.index] == null }
            .forEach { this[it.index] = (it.definition as HasDefaultValueDefinition<*>).default!! }
    }

    override fun toString() = list.joinToString(separator = ", ", prefix = "{", postfix = "}")
}

private fun mutableValueCreator(valueToChange: Any?, preserveValues: Boolean = false): Any? = when (valueToChange) {
    null -> null
    is List<*> -> valueToChange.mapTo(ArrayList(valueToChange.size)) {
        mutableValueCreator(it, preserveValues = true) ?: it
    }
    is Set<*> -> valueToChange.mapTo(LinkedHashSet(valueToChange.size)) {
        mutableValueCreator(it, preserveValues = true) ?: it
    }
    is Map<*, *> -> valueToChange.entries.associateTo(LinkedHashMap(valueToChange.size)) { (key, value) ->
        key to (mutableValueCreator(value, preserveValues = true) ?: value)
    }
    is Values<*> -> if (preserveValues) {
        Values(valueToChange.dataModel, valueToChange.values.copyAdding(emptyList()), valueToChange.context)
    } else {
        Values(valueToChange.dataModel, MutableValueItems(), valueToChange.context)
    }
    is TypedValue<*, *> -> MutableTypedValue(valueToChange.type, mutableValueCreator(valueToChange.value, preserveValues = true) as Any)
    else -> valueToChange
}
