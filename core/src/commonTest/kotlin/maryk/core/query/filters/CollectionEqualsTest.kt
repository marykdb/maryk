package maryk.core.query.filters

import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionEqualsTest {
    @Test
    fun stringCollectionsKeepContainmentSemanticsAcrossSizes() {
        val reference = TestMarykModel.ref { listOfString }
        for (size in listOf(15, 16, 17, 64, 8192)) {
            val actual = List(size) { "entry-$it-é🙂" }
            fun matches(expected: List<String>) = matchesFilter(Equals(reference with expected), { _, matcher -> matcher(actual) })
            assertTrue(matches(actual.reversed() + actual.take(3)))
            assertTrue(matches(actual.take(16)))
            assertFalse(matches(actual.drop(1) + "absent"))
            assertTrue(matches(emptyList()))
        }
        assertFalse(matchesFilter(Equals(reference with List(16) { "present" }), { _, matcher -> matcher(emptyList<String>()) }))
    }

    @Test
    fun collectionNormalizationStillPrecedesContainment() {
        val reference = TestMarykModel.ref { listOfString }
        val actual = List(32) { "ENTRY-$it" }
        val expected = List(16) { "entry-$it" }
        assertTrue(matchesFilter(
            Equals(reference with expected),
            { _, matcher -> matcher(actual) },
            normalizer = { _, value -> if (value is Collection<*>) value.map { (it as String).lowercase() } else value },
        ))
        assertFalse(matchesFilter(Equals(reference with expected), { _, matcher -> matcher(actual) }))
    }
}
