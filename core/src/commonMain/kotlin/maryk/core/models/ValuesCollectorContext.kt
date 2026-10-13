package maryk.core.models

import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem

internal data class ValuesCollectorFrame(val items: MutableValueItems, val setDefaults: Boolean)

internal expect val valuesCollectorFrames: MutableList<ValuesCollectorFrame>

// Public context used by DSL-capable members in wrappers and models
object ValuesCollectorContext {
    private val stack get() = valuesCollectorFrames
    fun push(setDefaults: Boolean): MutableValueItems = MutableValueItems().also {
        stack.add(ValuesCollectorFrame(it, setDefaults))
    }
    fun currentItems(): MutableValueItems? = stack.lastOrNull()?.items
    fun currentSetDefaults(): Boolean = stack.lastOrNull()?.setDefaults ?: true
    fun pop(): MutableValueItems = stack.removeAt(stack.lastIndex).items
    fun add(item: ValueItem?) { item?.let { currentItems()?.plusAssign(it) } }
}
