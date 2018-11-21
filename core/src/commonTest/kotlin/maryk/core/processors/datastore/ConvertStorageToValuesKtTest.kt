package maryk.core.processors.datastore

import maryk.lib.extensions.initByteArrayByHex
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ConvertStorageToValuesKtTest {
    @Test
    fun convertStorageToValues() {
        var qualifierIndex = -1
        val values = TestMarykModel.convertStorageToValues(
            getQualifier = {
                valuesAsStorables.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            processValue = { _, _ -> valuesAsStorables[qualifierIndex].second }
        )

        values shouldBe testMaryk
    }
}
