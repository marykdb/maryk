package maryk.core.properties.enum

import maryk.core.aggregations.AggregationRequestType.SumType
import maryk.core.aggregations.AggregationRequestType.ValueCountType
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.StringDefinition
import maryk.test.models.MarykTypeEnum.T1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.MarykTypeEnum.T3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiTypeEnumDefinitionTest {
    @Test
    fun isCompatible() {
        assertTrue {
            MultiTypeEnumDefinition("Test", { listOf(T1, T2, T3) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
            )
        }

        assertFalse {
            MultiTypeEnumDefinition("WRONG", { listOf(T1, T2, T3) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
            )
        }

        assertFalse {
            MultiTypeEnumDefinition("Test", { listOf(ValueCountType, SumType) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
            )
        }

        assertFalse {
            MultiTypeEnumDefinition("Test", { listOf(T2) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
            )
        }

        assertTrue {
            MultiTypeEnumDefinition(
                name = "Test",
                values = { listOf(T2) },
                reservedIndices = listOf(1u),
                reservedNames = listOf("T1", "Type1")
            ).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(T1, T2) })
            )
        }

        assertFalse {
            MultiTypeEnumDefinition("Test", { listOf(T1, T2) }).compatibleWith(
                MultiTypeEnumDefinition(
                    name = "Test",
                    values = { listOf(T1, T2)},
                    reservedNames = listOf("T4"),
                    reservedIndices = listOf(4u)
                )
            )
        }
    }

    @Test
    fun enumDefinitionIsCompatible() {
        open class MultiTypeTestEnum<T: Any>(
            index: UInt,
            override val definition: IsUsableInMultiType<T, *>?
        ) : IndexedEnumImpl<MultiTypeTestEnum<Any>>(index), MultiTypeEnum<T>

        val t1 = MultiTypeTestEnum(1u, StringDefinition(regEx = "[^&]+"))
        val t2 = MultiTypeTestEnum(1u, StringDefinition(regEx = "[^INCOMPATIBLE]+"))

        assertTrue {
            MultiTypeEnumDefinition("Test", { listOf(t1) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(t1) })
            )
        }

        assertFalse {
            MultiTypeEnumDefinition("Test", { listOf(t1) }).compatibleWith(
                MultiTypeEnumDefinition("Test", { listOf(t2) })
            )
        }
    }
}
