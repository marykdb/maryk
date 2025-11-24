package io.maryk.cli

import com.varabyte.kotter.foundation.input.Key
import com.varabyte.kotter.foundation.input.Keys

/**
 * Generic arrow-key friendly selector that highlights options and executes a callback on selection.
 */
class OptionSelectorInteraction<T>(
    options: List<Option<T>>,
    override val promptLabel: String,
    override val introLines: List<String>,
    private val onSelection: (Option<T>) -> InteractionResult,
    private val onCancel: () -> InteractionResult = { InteractionResult.Complete(listOf("Cancelled.")) },
    private val resolveSelection: (input: String, currentIndex: Int, options: List<Option<T>>) -> Selection = ::defaultResolve,
    initialIndex: Int = 0,
) : CliInteraction {
    private val options: List<Option<T>> = options.ifEmpty { error("OptionSelectorInteraction requires at least one option") }
    private var selectedIndex: Int = initialIndex.coerceIn(options.indices)

    override fun promptLines(): List<String> = buildList {
        options.forEachIndexed { index, option ->
            val marker = if (index == selectedIndex) ">" else " "
            add(" $marker ${index + 1}) ${option.label}")
        }
        add("Use arrows to move, Enter to select, or type a number. Type `cancel` to abort.")
    }

    override fun onInput(input: String): InteractionResult {
        val selection = resolveSelection(input.trim(), selectedIndex, options)
        return when (selection) {
            is Selection.Select -> {
                val newIndex = selection.index.coerceIn(options.indices)
                selectedIndex = newIndex
                onSelection(options[newIndex])
            }

            is Selection.Cancel -> onCancel()

            is Selection.Error -> InteractionResult.Stay(listOf(selection.message))
        }
    }

    override fun onKeyPressed(key: Key): InteractionKeyResult? {
        val previous = selectedIndex
        when (key) {
            Keys.UP -> if (selectedIndex > 0) selectedIndex -= 1
            Keys.DOWN -> if (selectedIndex < options.lastIndex) selectedIndex += 1
            else -> return null
        }
        return if (previous != selectedIndex) InteractionKeyResult.Rerender else null
    }

    data class Option<T>(val value: T, val label: String)

    sealed class Selection {
        data class Select(val index: Int) : Selection()
        data class Cancel(val reason: String? = null) : Selection()
        data class Error(val message: String) : Selection()
    }

    private companion object {
        fun <T> defaultResolve(
            input: String,
            currentIndex: Int,
            options: List<Option<T>>,
        ): Selection {
            if (input.isEmpty()) return Selection.Select(currentIndex)
            if (input.equals("cancel", ignoreCase = true)) return Selection.Cancel()

            val number = input.toIntOrNull()
            if (number != null) {
                val idx = number - 1
                return if (idx in options.indices) Selection.Select(idx) else Selection.Error("Option $number is out of range.")
            }

            return Selection.Error("Unrecognized choice `$input`. Use arrows or enter a number.")
        }
    }
}
