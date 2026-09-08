package maryk.core.values

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.properties.enum.invoke
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.test.models.Option
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import maryk.test.models.TestValueObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class ValuesToChangesTest {
    @Test
    fun convertValuesToChanges() {
        val values = TestMarykModel.create {
            string with "hello"
            int with 5
            embeddedValues with {
                value with "sub"
                model with {
                    value with "deep"
                }
            }
        }

        val changes = values.toChanges()
        expect(1) { changes.size }
        val change = changes[0] as Change

        expect(5) { change.referenceValuePairs.size }

        val pair1 = change.referenceValuePairs[0]
        assertEquals(TestMarykModel.ref { string }, pair1.reference)
        assertEquals("hello", pair1.value)

        val pair2 = change.referenceValuePairs[1]
        assertEquals(TestMarykModel.ref { int }, pair2.reference)
        assertEquals(5, pair2.value)

        val pair3 = change.referenceValuePairs[2]
        assertEquals(TestMarykModel.ref { enum }, pair3.reference)
        assertEquals(Option.V1, pair3.value)

        val pair4 = change.referenceValuePairs[3]
        assertEquals(TestMarykModel.ref { embeddedValues { value } }, pair4.reference)
        assertEquals("sub", pair4.value)

        val pair5 = change.referenceValuePairs[4]
        assertEquals(TestMarykModel.ref { embeddedValues { model { value } } }, pair5.reference)
        assertEquals("deep", pair5.value)
    }

    @Test
    fun convertCollectionsToChanges() {
        val values = TestMarykModel.create {
            string with "hello"
            int with 1
            list with listOf(3, 4)
            set with setOf(LocalDate(2017, 12, 5))
            map with mapOf(LocalTime(12, 23) to "yes")
            valueObject with TestValueObject(1, LocalDateTime(2018, 9, 3, 12, 30), true)
        }

        val change = values.toChanges()[0] as Change
        val pairs = change.referenceValuePairs.associate { it.reference to it.value }

        assertEquals("hello", pairs[TestMarykModel.ref { string }])
        assertEquals(1, pairs[TestMarykModel.ref { int }])
        assertEquals(listOf(3, 4), pairs[TestMarykModel.ref { list }])
        assertEquals(setOf(LocalDate(2017, 12, 5)), pairs[TestMarykModel.ref { set }])
        assertEquals(mapOf(LocalTime(12, 23) to "yes"), pairs[TestMarykModel.ref { map }])
        assertEquals(TestValueObject(1, LocalDateTime(2018, 9, 3, 12, 30), true), pairs[TestMarykModel.ref { valueObject }])
        assertEquals(Option.V1, pairs[TestMarykModel.ref { enum }])
    }

    @Test
    fun convertSimpleMultiTypeToChanges() {
        val values = TestMarykModel.create {
            string with "hello"
            int with 1
            multi with S1("s1value")
        }

        val change = values.toChanges()[0] as Change
        val pairs = change.referenceValuePairs.associate { it.reference to it.value }

        assertEquals("hello", pairs[TestMarykModel.ref { string }])
        assertEquals(1, pairs[TestMarykModel.ref { int }])
        assertEquals("s1value", pairs[TestMarykModel.ref { multi.atType(S1) }])
        assertEquals(Option.V1, pairs[TestMarykModel.ref { enum }])
    }

    @Test
    fun convertEmbeddedMultiTypeToChanges() {
        val values = TestMarykModel.create {
            string with "hello"
            int with 1
            multi with S3 {
                value with "multi"
                model with {
                    value with "deep"
                }
            }
        }

        val change = values.toChanges()[0] as Change
        val pairs = change.referenceValuePairs.associate { it.reference to it.value }

        assertEquals("hello", pairs[TestMarykModel.ref { string }])
        assertEquals(1, pairs[TestMarykModel.ref { int }])
        assertEquals("multi", pairs[TestMarykModel.ref { multi.withType(S3) { value } }])
        assertEquals("deep", pairs[TestMarykModel.ref { multi.withType(S3) { model { value } } }])
        assertEquals(Option.V1, pairs[TestMarykModel.ref { enum }])
    }
}
