package maryk.datastore.shared.helpers

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.properties.references.IsPropertyReference
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FuzzyQualifierTest {
    @Test
    fun decodesMapKeysAndSetValuesFromQualifierSlices() {
        val time = LocalTime(12, 30)
        val date = LocalDate(2020, 1, 2)
        val mapQualifier = TestMarykModel.ref { map at time }.toStorageByteArray()
        val setQualifier = TestMarykModel.ref { set item date }.toStorageByteArray()
        val prefix = byteArrayOf(99, 98)
        val suffix = byteArrayOf(97)
        val decodedTime: LocalTime? = readFuzzyValue(TestMarykModel.ref { map.anyKey() },
            prefix + mapQualifier + suffix, prefix.size, mapQualifier.size)
        val decodedDate: LocalDate? = readFuzzyValue(TestMarykModel.ref { set.any() },
            prefix + setQualifier + suffix, prefix.size, setQualifier.size)
        assertEquals(time, decodedTime)
        assertEquals(date, decodedDate)
    }

    @Test
    fun erasedWildcardReferencesDecodeScalarValues() {
        val time = LocalTime(12, 30)
        val date = LocalDate(2020, 1, 2)
        val mapReference: IsPropertyReference<*, *, *> = TestMarykModel.ref { map.anyKey() }
        val setReference: IsPropertyReference<*, *, *> = TestMarykModel.ref { set.any() }
        val mapQualifier = TestMarykModel.ref { map at time }.toStorageByteArray()
        val setQualifier = TestMarykModel.ref { set item date }.toStorageByteArray()

        assertEquals(time, readFuzzyValue(mapReference, mapQualifier, 0, mapQualifier.size))
        assertEquals(date, readFuzzyValue(setReference, setQualifier, 0, setQualifier.size))
    }

    @Test
    fun truncatedQualifiersDoNotMatch() {
        val qualifier = TestMarykModel.ref { map at LocalTime(12, 30) }.toStorageByteArray()
        val setQualifier = TestMarykModel.ref { set item LocalDate(2020, 1, 2) }.toStorageByteArray()
        assertNull(readFuzzyValue(TestMarykModel.ref { map.anyKey() }, qualifier, 0, qualifier.size - 1))
        assertNull(readFuzzyValue(TestMarykModel.ref { set.any() }, setQualifier, 0, setQualifier.size - 1))
        assertNull(readFuzzyValue(TestMarykModel.ref { string }, qualifier, 0, qualifier.size))
    }

    @Test
    fun combinesOnlyTheRequestedKeyAndQualifierSlices() {
        assertContentEquals(byteArrayOf(1, 2, 3, 4), matchedQualifier(
            byteArrayOf(99, 1, 2, 98), 1, 2, byteArrayOf(97, 3, 4, 96), 1, 2))
    }
}
