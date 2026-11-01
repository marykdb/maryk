package maryk.core.query

import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class RequestContextFactoryTest {
    @Test
    fun independentOperationsDoNotShareMutableParsingState() {
        val importing = TestMarykModel.createRequestContext()
        importing.reference = TestMarykModel.ref { string }
        importing.legacyValueStorage = true
        importing.dataModels.clear()

        val exporting = TestMarykModel.createRequestContext()
        assertSame(TestMarykModel, exporting.dataModel)
        assertSame(TestMarykModel, exporting.dataModels.getValue(TestMarykModel.Meta.name).get())
        assertNull(exporting.reference)
        assertFalse(exporting.legacyValueStorage)
    }
}
