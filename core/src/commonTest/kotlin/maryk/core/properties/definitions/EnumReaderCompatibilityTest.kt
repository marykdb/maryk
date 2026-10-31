package maryk.core.properties.definitions

import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.test.models.MarykTypeEnum
import maryk.test.models.MarykTypeEnum.UnknownMarykTypeEnum
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class ReaderOption(override val index: UInt) : IndexedEnumComparable<ReaderOption> {
    Known(1u), Removed(2u);
    override val alternativeNames: Set<String>? = null
}

class EnumReaderCompatibilityTest {
    @Test
    fun reservedEnumIdsStillRequireAReaderForStoredValues() {
        val source = EnumDefinition(enum = IndexedEnumDefinition(ReaderOption::class, { ReaderOption.entries }))
        val closed = EnumDefinition(enum = IndexedEnumDefinition(ReaderOption::class, { listOf(ReaderOption.Known) },
            reservedIndices = listOf(2u), reservedNames = listOf("Removed")))
        assertFalse(closed.compatibleWith(source))
        val readable = EnumDefinition(enum = IndexedEnumDefinition(ReaderOption::class, { listOf(ReaderOption.Known) },
            reservedIndices = listOf(2u), reservedNames = listOf("Removed"),
            unknownCreator = { index, _ -> require(index == 2u); ReaderOption.Removed }))
        assertTrue(readable.compatibleWith(source))
    }

    @Test
    fun reservedTypedCaseIdsCannotDiscardTheirStoredPayloadDefinition() {
        val source = MultiTypeDefinition(typeEnum = MultiTypeEnumDefinition(MarykTypeEnum::class,
            { listOf(MarykTypeEnum.T1, MarykTypeEnum.T2) }))
        val target = MultiTypeDefinition(typeEnum = MultiTypeEnumDefinition(MarykTypeEnum::class,
            { listOf(MarykTypeEnum.T1) }, reservedIndices = listOf(2u), reservedNames = listOf("T2"),
            unknownCreator = ::UnknownMarykTypeEnum))
        assertFalse(target.compatibleWith(source))
    }
}
