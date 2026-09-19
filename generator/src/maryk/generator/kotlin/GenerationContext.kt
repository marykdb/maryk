package maryk.generator.kotlin

import maryk.core.properties.enum.IsIndexedEnumDefinition

/**
 * Stores values which are possibly needed later in the generation context
 */
class GenerationContext(
    internal val enums: MutableList<IsIndexedEnumDefinition<*>> = mutableListOf()
)
