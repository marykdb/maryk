package maryk.core.properties.enum

import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexedEnumDefinitionTest {
    @Test
    fun isCompatible() {
        assertTrue {
            IndexedEnumDefinition("Test", { arrayOf(V1, V2, V3)}).compatibleWith(
                IndexedEnumDefinition("Test", { arrayOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("WRONG", { arrayOf(V1, V2, V3)}).compatibleWith(
                IndexedEnumDefinition("Test", { arrayOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { arrayOf(E1, V2)}).compatibleWith(
                IndexedEnumDefinition("Test", { arrayOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { arrayOf(V2)}).compatibleWith(
                IndexedEnumDefinition("Test", { arrayOf(V1, V2)})
            )
        }

        assertTrue {
            IndexedEnumDefinition(
                name = "Test",
                values = { arrayOf(V2)},
                reservedIndices = listOf(1u),
                reservedNames = listOf("V1")
            ).compatibleWith(
                IndexedEnumDefinition("Test", { arrayOf(V1, V2)})
            )
        }

        assertFalse {
            IndexedEnumDefinition("Test", { arrayOf(V1, V2)}).compatibleWith(
                IndexedEnumDefinition(
                    name = "Test",
                    values = { arrayOf(V1, V2) },
                    reservedNames = listOf("V4"),
                    reservedIndices = listOf(4u)
                )
            )
        }
    }
}
