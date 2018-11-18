@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "FunctionName")

package maryk.core.values

interface IsValueItems: Iterable<ValueItem> {
    val size: Int

    operator fun get(index: Int): Any?

    fun contains(index: Int): Boolean

    fun copyAdding(toAdd: Array<ValueItem>): IsValueItems
}

interface IsValueItemsImpl: IsValueItems {
    val list: List<ValueItem>
}

fun ValueItems(): IsValueItems = MutableValueItems()
fun ValueItems(vararg item: ValueItem): IsValueItems = MutableValueItems(*item)

inline class MutableValueItems(
    override val list: MutableList<ValueItem>
): IsValueItemsImpl {
    override val size get() = list.size

    constructor() : this(mutableListOf())

    constructor(vararg item: ValueItem) : this(mutableListOf(*item))

    operator fun plusAssign(valueItem: ValueItem) {
        this.searchItemByIndex(valueItem.index).let {
            when {
                it < 0 -> list.add((it * -1) -1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    operator fun set(index: Int, value: Any) {
        this.searchItemByIndex(index).let {
            val valueItem = ValueItem(index, value)
            when {
                it < 0 -> list.add((it * -1) -1, valueItem)
                else -> list.set(it, valueItem)
            }
        }
    }

    fun remove(index: Int) = this.searchItemByIndex(index).let {
        when {
            it < 0 -> null
            else -> list.removeAt(it)
        }
    }

    override fun contains(index: Int): Boolean {
        return 0 <= list.binarySearch { it.index.compareTo(index) }
    }

    override operator fun get(index: Int): Any? {
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

    private fun searchItemByIndex(index: Int): Int {
        // Index can never be at a higher spot in list than index itself
        return list.binarySearch(toIndex = minOf(index, list.size)) { it.index.compareTo(index) }
    }

    override fun toString() = this.list.joinToString(separator = ", ", prefix = "{", postfix = "}")
}
