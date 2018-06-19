package maryk.generator.kotlin

import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Stores values which are possibly needed later in the Kotlin generation context
 */
class KotlinGenerationContext(
    internal val enums: MutableList<IndexedEnumDefinition<*>> = mutableListOf()
)
