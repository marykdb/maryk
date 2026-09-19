package maryk.core.models

import maryk.core.values.div
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class ValuesDslTest {
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
