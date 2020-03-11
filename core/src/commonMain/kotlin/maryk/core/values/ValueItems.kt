@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "FunctionName")

package maryk.core.values

import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.PropRefGraph

interface IsValueItems : Iterable<ValueItem> {
    val size: Int

    operator fun get(index: UInt): Any?

    fun getValueItem(index: UInt): ValueItem?

    fun contains(index: UInt): Boolean

    fun copyAdding(toAdd: Iterable<ValueItem>): IsValueItems

    fun copySelecting(select: IsPropRefGraph<*>): IsValueItems
}

interface IsValueItemsImpl : IsValueItems {
    val list: List<ValueItem>

    override val size get() = list.size

    override fun contains(index: UInt) =
        0 <= list.binarySearch { it.index.compareTo(index) }

    override operator fun get(index: UInt): Any? {
        this.list.searchItemByIndex(index).let {
            return when {
                it < 0 -> null
                else -> list[it].value
            }
        }
    }

    override fun getValueItem(index: UInt): ValueItem? {
        this.list.searchItemByIndex(index).let {
            return when {
                it < 0 -> null
                else -> list[it]
            }
        }
    }

    override fun iterator() = object : Iterator<ValueItem> {
        var index = 0
        override fun hasNext() = index < list.size
        override fun next() = list[index++]
    }

    override fun copyAdding(toAdd: Iterable<ValueItem>) = MutableValueItems(this.list.toMutableList()).also { items ->
        toAdd.forEach {
            items += it
        }
    }

    override fun copySelecting(select: IsPropRefGraph<*>) = MutableValueItems(
        list = this.mapNotNull { valueItem ->
            if (!select.contains(valueItem.index)) {
                null
            } else {
                if (valueItem.value is Values<*, *>) {
                    (select.selectNodeOrNull(valueItem.index) as? PropRefGraph<*, *, *>)?.let { subSelect ->
                        ValueItem(valueItem.index, valueItem.value.filterWithSelect(subSelect))
                    } ?: valueItem
                } else valueItem
            }
        }.toMutableList()
    )
}

internal fun List<ValueItem>.searchItemByIndex(index: UInt): Int =
    // Index can never be at a higher spot in list than index itself
    binarySearch(toIndex = minOf(index.toInt(), size)) { it.index.compareTo(index) }

val EmptyValueItems: IsValueItems = MutableValueItems()
fun ValueItems(vararg item: ValueItem): IsValueItems = MutableValueItems(*item)

inline class MutableValueItems(
    override val list: MutableList<ValueItem>
) : IsValueItemsImpl {
    override val size get() = list.size

    constructor() : this(mutableListOf())

    constructor(vararg item: ValueItem) : this(mutableListOf(*item))

    operator fun plusAssign(valueItem: ValueItem) {
        this.list.searchItemByIndex(valueItem.index).let {
            when {
                it < 0 -> list.add((it * -1) - 1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    operator fun set(index: UInt, value: Any) {
        this.list.searchItemByIndex(index).let {
            val valueItem = ValueItem(index, value)
            when {
                it < 0 -> list.add((it * -1) - 1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    fun remove(index: UInt) = this.list.searchItemByIndex(index).let {
        when {
            it < 0 -> null
            else -> list.removeAt(it)
        }
    }

    override fun toString() = this.list.joinToString(separator = ", ", prefix = "{", postfix = "}")
}
