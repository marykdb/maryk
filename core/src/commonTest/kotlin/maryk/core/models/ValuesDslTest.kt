package maryk.core.models

import maryk.core.values.div
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.expect

class ValuesDslTest {
    @Test
    fun nestedFailureRestoresOuterFrameAndDefaults() {
        val outer = TestMarykModel.create(setDefaults = false) {
            string with "haha-outer"
            assertFailsWith<IllegalStateException> {
                TestMarykModel.create {
                    string with "haha-inner"
                    error("Abort inner builder")
                }
            }
            uint with 3u
        }
        assertEquals("haha-outer", outer { string })
        assertEquals(3u, outer { uint })
        assertNull(outer.values[TestMarykModel.enum.index])
        assertNull(ValuesCollectorContext.currentItems())
        assertEquals(true, ValuesCollectorContext.currentSetDefaults())
    }

    @Test
    fun buildValuesWithDsl() {
        val v = TestMarykModel.create {
            string with "wrong"
            int with 999
            uint with 53u
            double with 2.3
        }

        expect("wrong") { v { string } }
        expect(999) { v { int } }
        expect(53u) { v { uint } }
    }

    @Test
    fun buildNestedEmbeddedValues() {
        val v = TestMarykModel.create {
            embeddedValues with {
                value with "X"
            }
        }

        expect("X") { v { embeddedValues } / { value } }
    }
}
