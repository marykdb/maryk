package maryk.core.properties.enum

import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.Option
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

enum class A(override val index: UInt, override val alternativeNames: Set<String>? = null): IndexedEnumComparable<A> {
    V1(1u),
    V2(2u, setOf("VERSION2")),
    V3(3u, setOf("VERSION3")),
    Unknown(4u);
}

class IndexedEnumDefinitionTest {
    @Test
    fun isCompatible() {
        assertTrue {
            IndexedEnumDefinition("Test", { listOf(V1, V2, V3)}).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("WRONG", { listOf(V1, V2, V3)}).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { listOf(E1, V2)}).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { listOf(V2)}).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V1, V2)})
            )
        }

        assertTrue {
            IndexedEnumDefinition(
                name = "Test",
                values = { listOf(V2)},
                reservedIndices = listOf(1u),
                reservedNames = listOf("V1")
            ).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { listOf(V1, V2)}).compatibleWith(
                IndexedEnumDefinition(
                    name = "Test",
                    values = { listOf(V1, V2) },
                    reservedNames = listOf("V4"),
                    reservedIndices = listOf(4u)
                )
            )
        }
    }
}
