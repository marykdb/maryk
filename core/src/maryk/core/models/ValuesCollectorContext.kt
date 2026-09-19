package maryk.core.models

import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem

// Public context used by DSL-capable members in wrappers and models
object ValuesCollectorContext {
    private data class Frame(val items: MutableValueItems, val setDefaults: Boolean)
    private val stack = mutableListOf<Frame>()
    fun push(setDefaults: Boolean): MutableValueItems = MutableValueItems().also {
        stack.add(Frame(it, setDefaults))
    }
    fun currentItems(): MutableValueItems? = stack.lastOrNull()?.items
    fun currentSetDefaults(): Boolean = stack.lastOrNull()?.setDefaults ?: true
    fun pop(): MutableValueItems = stack.removeAt(stack.lastIndex).items
    fun add(item: ValueItem?) { item?.let { currentItems()?.plusAssign(it) } }
}
