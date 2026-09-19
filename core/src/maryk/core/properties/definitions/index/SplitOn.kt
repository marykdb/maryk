package maryk.core.properties.definitions.index

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum

sealed class SplitOn(index: UInt) : IndexedEnumImpl<SplitOn>(index), IsCoreEnum {
    data object Whitespace : SplitOn(1u)
    data object WordBoundary : SplitOn(2u)

    companion object : IndexedEnumDefinition<SplitOn>(SplitOn::class, { listOf(Whitespace, WordBoundary) })
}
