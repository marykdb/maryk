package maryk.core.properties.enum

import maryk.core.properties.enum.IndexedEnumComparable.Companion.invoke
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.Option.V0
import maryk.test.models.Option.V1
import maryk.test.models.Option.V2
import maryk.test.models.Option.V3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexedEnumDefinitionTest {
    @Test
    fun reorderingCasesWithStableIndicesIsCompatible() {
        val first = invoke(1u, "first")
        val second = invoke(2u, "second")

        assertTrue {
            IndexedEnumDefinition("Test", { listOf(second, first) }).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(first, second) })
            )
        }
    }

    @Test
    fun emptyCasesAreCompatibleWithEmptyStoredCases() {
        val empty = IndexedEnumDefinition<IndexedEnumComparable<Any>>("Test", ::emptyList)

        assertTrue {
            empty.compatibleWith(IndexedEnumDefinition("Test", ::emptyList))
        }
    }

    @Test
    fun emptyCasesNeedReservationsForStoredCases() {
        val first = invoke(1u, "first")
        val stored = IndexedEnumDefinition("Test", { listOf(first) })
        val empty = IndexedEnumDefinition<IndexedEnumComparable<Any>>("Test", ::emptyList)

        assertFalse {
            empty.compatibleWith(stored)
        }
        assertTrue {
            IndexedEnumDefinition<IndexedEnumComparable<Any>>(
                name = "Test",
                values = ::emptyList,
                reservedIndices = listOf(1u),
                reservedNames = listOf("first")
            ).compatibleWith(stored)
        }
    }

    @Test
    fun removingStoredCasesNeedsReservations() {
        val first = invoke(1u, "first")
        val second = invoke(2u, "second")
        val stored = IndexedEnumDefinition("Test", { listOf(second, first) })

        assertFalse {
            IndexedEnumDefinition("Test", { listOf(second) }).compatibleWith(stored)
        }
        assertTrue {
            IndexedEnumDefinition(
                name = "Test",
                values = { listOf(second) },
                reservedIndices = listOf(1u),
                reservedNames = listOf("first")
            ).compatibleWith(stored)
        }
    }

    @Test
    fun reorderingCasesWithChangedNameIsIncompatible() {
        val first = invoke(1u, "first")
        val renamedFirst = invoke(1u, "renamedFirst")
        val second = invoke(2u, "second")

        assertFalse {
            IndexedEnumDefinition("Test", { listOf(second, renamedFirst) }).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(first, second) })
            )
        }
    }

    @Test
    fun duplicateCaseIndicesAreRejectedDuringCompatibility() {
        val first = invoke(1u, "first")
        val second = invoke(1u, "second")

        assertFailsWith<IllegalArgumentException> {
            IndexedEnumDefinition("Test", { listOf(first, second) }).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(second, first) })
            )
        }
    }

    @Test
    fun storedDuplicateCaseIndicesAreRejectedDuringCompatibility() {
        val first = invoke(1u, "first")
        val second = invoke(1u, "second")

        assertFailsWith<IllegalArgumentException> {
            IndexedEnumDefinition("Test", { listOf(first) }).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(second, first) })
            )
        }
    }

    @Test
    fun compatibilityUsesOneCaseSnapshotPerDefinition() {
        val first = invoke(1u, "first")
        val renamedFirst = invoke(1u, "renamedFirst")
        val second = invoke(2u, "second")
        var newCalls = 0
        var storedCalls = 0
        val newDefinition = IndexedEnumDefinition(
            name = "Test",
            values = {
                newCalls++
                if (newCalls == 1) listOf(second, first) else listOf(second, renamedFirst)
            }
        )
        val storedDefinition = IndexedEnumDefinition(
            name = "Test",
            values = {
                storedCalls++
                listOf(first, second)
            }
        )

        assertTrue { newDefinition.compatibleWith(storedDefinition) }
        assertEquals(1, newCalls)
        assertEquals(1, storedCalls)
    }

    @Test
    fun isCompatible() {
        assertTrue {
            IndexedEnumDefinition("Test", { listOf(V0, V1, V2, V3)}).compatibleWith(
                IndexedEnumDefinition("Test", { listOf(V0, V1, V2)})
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
