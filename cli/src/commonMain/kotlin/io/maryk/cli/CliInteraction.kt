package io.maryk.cli

import com.varabyte.kotter.foundation.input.InputCompleter
import com.varabyte.kotter.foundation.input.Key

/**
 * Represents a multi-step interaction that consumes user input directly,
 * outside of the standard command parsing loop.
 */
interface CliInteraction {
    val promptLabel: String
    val introLines: List<String>
    val allowViewerOnComplete: Boolean
        get() = true

    /**
     * Lines that should be rendered before the prompt on every iteration while this interaction is active.
     * Use this to display dynamic state such as a highlighted selection.
     */
    fun promptLines(): List<String> = emptyList()

    fun onInput(input: String): InteractionResult

    /**
     * Optional key handler for interactions that support direct key navigation (e.g. arrow selection).
     * Return [InteractionKeyResult.Rerender] when the UI should be redrawn after handling the key.
     */
    fun onKeyPressed(key: Key): InteractionKeyResult? = null

    /** Optional completer for interactive prompts. */
    fun inputCompleter(): InputCompleter? = null
}

sealed class InteractionResult {
    data class Continue(
        val next: CliInteraction,
        val lines: List<String> = emptyList(),
        val showIntro: Boolean = true,
    ) : InteractionResult()

    data class Stay(
        val lines: List<String> = emptyList(),
    ) : InteractionResult()

    data class Complete(
        val lines: List<String> = emptyList(),
        val saveContext: SaveContext? = null,
        val deleteContext: DeleteContext? = null,
    ) : InteractionResult()
}

sealed class InteractionKeyResult {
    data object Rerender : InteractionKeyResult()
    data class Complete(
        val lines: List<String> = emptyList(),
        val skipRender: Boolean = true,
        val saveContext: SaveContext? = null,
        val deleteContext: DeleteContext? = null,
    ) : InteractionKeyResult()
}
