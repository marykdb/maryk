package maryk.core.processors.datastore

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.models.emptyValues
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.invoke
import maryk.lib.extensions.initByteArrayByHex
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadStorageToValuesKtTest {
    @Test
    fun convertStorageToValues() {
        var qualifierIndex = -1
        val values = TestMarykModel.readStorageToValues(
            getQualifier = { resultHander ->
                val qualifier = valuesAsStorables.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHander({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            processValue = { _, _ -> valuesAsStorables[qualifierIndex].second }
        )

        assertEquals(testMaryk, values)
    }

    @Test
    fun convertStorageToComplexValues() {
        var qualifierIndex = -1
        val values = ComplexModel.readStorageToValues(
            getQualifier = { resultHandler ->
                val qualifier = complexValuesAsStorables.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            processValue = { _, _ -> complexValuesAsStorables[qualifierIndex].second }
        )

        assertEquals(complexValues, values)
    }

    @Test
    fun convertStorageToValuesWithNullsInComplex() {
        var qualifierIndex = -1
        val values = TestMarykModel.readStorageToValues(
            getQualifier = { resultHandler ->
                val qualifier = valuesAsStorablesWithNulls.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            processValue = { _, _ -> valuesAsStorablesWithNulls[qualifierIndex].second }
        )

        assertEquals(
            TestMarykModel.create(setDefaults = false) {
                set += setOf(
                    LocalDate(1981, 12, 5)
                )
                map += mapOf(
                    LocalTime(12, 23, 34) to "twelve"
                )
                embeddedValues += EmbeddedMarykModel.create {
                    value += "test"
                    model += EmbeddedMarykModel.run {
                        create{}
                    }
                }
                listOfString += listOf("v1")
                setOfString += setOf("def")
            },
            values
        )
    }

    @Test
    fun convertStorageToValuesWithNullMapListSetValues() {
        // This is incorrect data but still the processor should skip the complex values
        val valuesUnset = arrayOf(
            "4b" to null, // set
            "4b80001104" to LocalDate(1981, 12, 5),
            "54" to null, // map
            "540300ae46" to "twelve",
            "66" to null, // embeddedValues
            "6609" to "test",
            "7a" to null, // listOfString
            "7a00000000" to "v1"
        )

        var qualifierIndex = -1
        val values = TestMarykModel.readStorageToValues(
            getQualifier = { resultHandler ->
                val qualifier = valuesUnset.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            processValue = { _, _ -> valuesUnset[qualifierIndex].second }
        )

        assertEquals(
            TestMarykModel.emptyValues(),
            values
        )
    }

    @Test
    fun convertStorageToValuesWithWrongMultis() {
        // This is incorrect data but still the processor should skip the complex type ids
        val valuesUnset = arrayOf(
            "69" to S1("test"),
            "691d" to Unit,
            "691d09" to "m3"
        )

        var qualifierIndex = -1
        val values = TestMarykModel.readStorageToValues(
            getQualifier = { resultHandler ->
                val qualifier = valuesUnset.getOrNull(++qualifierIndex)?.let {
                    initByteArrayByHex(it.first)
                }
                qualifier?.let { resultHandler({ qualifier[it] }, qualifier.size); true } == true
            },
            select = null,
            processValue = { _, _ -> valuesUnset[qualifierIndex].second }
        )

        assertEquals(
            TestMarykModel.create(setDefaults = false) {
                multi += TypedValue(S1, "test")
            },
            values
        )
    }
}
