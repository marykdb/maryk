@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "FunctionName")

package maryk.core.values

import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.graph.PropRefGraph

interface IsValueItems : Iterable<ValueItem> {
    val size: Int

    operator fun get(index: UInt): Any?

    fun contains(index: UInt): Boolean

    fun copyAdding(toAdd: Array<ValueItem>): IsValueItems

    fun copySelecting(select: IsPropRefGraph<*>): IsValueItems
}

interface IsValueItemsImpl : IsValueItems {
    val list: List<ValueItem>
}

val EmptyValueItems: IsValueItems = MutableValueItems()
fun ValueItems(vararg item: ValueItem): IsValueItems = MutableValueItems(*item)

inline class MutableValueItems(
    override val list: MutableList<ValueItem>
) : IsValueItemsImpl {
    override val size get() = list.size

    constructor() : this(mutableListOf())

    constructor(vararg item: ValueItem) : this(mutableListOf(*item))

    operator fun plusAssign(valueItem: ValueItem) {
        this.searchItemByIndex(valueItem.index).let {
            when {
                it < 0 -> list.add((it * -1) - 1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    operator fun set(index: UInt, value: Any) {
        this.searchItemByIndex(index).let {
            val valueItem = ValueItem(index, value)
            when {
                it < 0 -> list.add((it * -1) - 1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    fun remove(index: UInt) = this.searchItemByIndex(index).let {
        when {
            it < 0 -> null
            else -> list.removeAt(it)
        }
    }

    override fun contains(index: UInt): Boolean {
        return 0 <= list.binarySearch { it.index.compareTo(index) }
    }

    override operator fun get(index: UInt): Any? {
        this.searchItemByIndex(index).let {
            return when {
                it < 0 -> null
                else -> list[it].value
            }
        }
    }

    override fun iterator() = object : Iterator<ValueItem> {
        var index = 0
        override fun hasNext() = index < list.size
        override fun next() = list[index++]
    }

    override fun copyAdding(toAdd: Array<ValueItem>) = MutableValueItems(this.list.toMutableList()).also { items ->
        toAdd.forEach {
            items += it
        }
    }

    override fun copySelecting(select: IsPropRefGraph<*>) = MutableValueItems(
        list = this.list
            .filter { select.contains(it.index) }
            .map {
                if (it.value is Values<*, *>) {
                    (select.selectNodeOrNull(it.index) as? PropRefGraph<*, *, *>)?.let { subSelect ->
                        ValueItem(it.index,  it.value.filterWithSelect(subSelect))
                    } ?: it
                } else {
                    it
                }
            }.toMutableList()
    )

    private fun searchItemByIndex(index: UInt): Int {
        // Index can never be at a higher spot in list than index itself
        return list.binarySearch(toIndex = minOf(index.toInt(), list.size)) { it.index.compareTo(index) }
    }

    override fun toString() = this.list.joinToString(separator = ", ", prefix = "{", postfix = "}")
}
