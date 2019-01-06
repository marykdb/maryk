package maryk.core.processors.datastore

import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

class ReadStorageToValuesKtTest {
    @Test
    fun convertStorageToValues() {
        var qualifierIndex = -1
        val values = TestMarykModel.convertStorageToValues(
            getQualifier = {
                valuesAsStorables.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            select = null,
            processValue = { _, _ -> valuesAsStorables[qualifierIndex].second }
        )

        values shouldBe testMaryk
    }

    @Test
    fun convertStorageToComplexValues() {
        var qualifierIndex = -1
        val values = ComplexModel.convertStorageToValues(
            getQualifier = {
                complexValuesAsStorables.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            select = null,
            processValue = { _, _ -> complexValuesAsStorables[qualifierIndex].second }
        )

        values shouldBe complexValues
    }

    @Test
    fun convertStorageToValuesWithNullsInComplex() {
        var qualifierIndex = -1
        val values = TestMarykModel.convertStorageToValues(
            getQualifier = {
                valuesAsStorablesWithNulls.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
            },
            select = null,
            processValue = { _, _ -> valuesAsStorablesWithNulls[qualifierIndex].second }
        )

        values shouldBe TestMarykModel.values {
            mapNonNulls(
                set with setOf(
                    Date(1981, 12, 5)
                ),
                map with mapOf(
                    Time(12, 23, 34) to "twelve"
                ),
                embeddedValues with EmbeddedMarykModel.values {
                    mapNonNulls(
                        value with "test",
                        model with EmbeddedMarykModel.values { mapNonNulls() }
                    )
                },
                listOfString with listOf("v1"),
                setOfString with setOf("def")
            )
        }
    }
}