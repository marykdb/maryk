package maryk.datastore.indexeddb

import maryk.core.query.pairs.with
import maryk.core.values.ValueItem
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.Float64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object JavaScriptNaNBoundaryRecord : RootDataModel<JavaScriptNaNBoundaryRecord>() {
    val number by number(1u, type = Float64)
}

class JavaScriptNaNBoundaryTest {
    @Test
    fun valueContainersPreserveNaNWithoutRequiringItsPayload() {
        // V8 can canonicalize the first numeric field or array element even before a codec runs.
        // A future engine that preserves these bits is also valid; raw payload equality is not
        // a JavaScript Number guarantee. Wasm keeps the strict raw-pattern lifecycle tests.
        assertFalse(supportsDistinctNaNPayloadsForTests())
        repeat(128) {
            val value = Double.fromBits(0x7ff8000000000001L)
            assertTrue((ValueItem(1u, value).value as Double).isNaN())
            assertTrue((JavaScriptNaNBoundaryRecord.ref { number } with value).value.isNaN())
        }
        for (value in listOf(0.0, -0.0, Double.MIN_VALUE, 1.0)) {
            assertEquals(value.toRawBits(), (ValueItem(1u, value).value as Double).toRawBits())
            assertEquals(value.toRawBits(), (JavaScriptNaNBoundaryRecord.ref { number } with value).value.toRawBits())
        }
    }
}
