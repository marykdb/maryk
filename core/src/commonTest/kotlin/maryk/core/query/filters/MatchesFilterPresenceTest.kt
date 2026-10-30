package maryk.core.query.filters

import kotlinx.datetime.LocalTime
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class MatchesFilterPresenceTest {
    @Test
    fun nestedLogicalFiltersUsePresenceWithoutDecodingValues() {
        val present = TestMarykModel.ref { listOfString }
        val missing = TestMarykModel.ref { embeddedValues }
        val fuzzy = TestMarykModel.ref { listOfString.any() }
        val missingEntry = TestMarykModel.ref { map at LocalTime(12, 0) }
        val visited = mutableListOf<AnyPropertyReference>()
        val filter = And(
            Exists(present),
            Or(Exists(missing), Not(Exists(fuzzy))),
            Not(Exists(missingEntry)),
        )
        assertTrue(matchesFilterWithPresence(
            filter,
            valueMatcher = { _, _ -> fail("Exists must not decode a stored count or embedded marker") },
            presenceMatcher = { reference -> visited += reference; reference == present },
        ))
        assertEquals(listOf<AnyPropertyReference>(present, missing, fuzzy, missingEntry), visited)
        assertFalse(matchesFilterWithPresence(Not(Exists(present)), { _, _ -> fail("No value decoding") }, { true }))
    }

    @Test
    fun concreteAndWildcardReferencesReachThePresenceMatcherUnchanged() {
        val references = listOf(
            TestMarykModel.ref { listOfString }, TestMarykModel.ref { embeddedValues },
            TestMarykModel.ref { listOfString at 0u }, TestMarykModel.ref { listOfString.any() },
            TestMarykModel.ref { map.anyValue() }, TestMarykModel.ref { map.anyKey() }, TestMarykModel.ref { setOfString.any() },
        )
        var offset = 0
        assertTrue(matchesFilterWithPresence(
            Exists(references),
            valueMatcher = { _, _ -> fail("Presence must not decode values") },
            presenceMatcher = { reference -> assertSame(references[offset++], reference); true },
        ))
        assertEquals(references.size, offset)
        assertTrue(matchesFilterWithPresence(null, { _, _ -> fail("No filter") }, { fail("No filter") }))
    }

    @Test
    fun comparisonsNormalizersAndSearchCallbacksRetainTheirExistingRouting() {
        val scalar = TestMarykModel.ref { string }
        val collection = TestMarykModel.ref { listOfString }
        val visits = mutableListOf<String>()
        assertTrue(matchesFilterWithPresence(
            And(Exists(collection), Equals(scalar with "HELLO"), Matches("search" with "token"),
                MatchesPrefix("search" with "tok"), MatchesRegEx("search" with Regex("tok.*"))),
            valueMatcher = { reference, matcher -> assertSame(scalar, reference); visits += "value"; matcher(" hello ") },
            presenceMatcher = { reference -> assertSame(collection, reference); visits += "presence"; true },
            normalizer = { reference, value -> assertSame(scalar, reference); (value as? String)?.trim()?.lowercase() ?: value },
            searchMatcher = { name, value -> visits += "search"; name == "search" && value == "token" },
            searchPrefixMatcher = { name, value -> visits += "prefix"; name == "search" && value == "tok" },
            searchRegexMatcher = { name, value -> visits += "regex"; name == "search" && value.matches("token") },
        ))
        assertEquals(listOf("presence", "value", "search", "prefix", "regex"), visits)
    }

    @Test
    fun legacyMatcherKeepsItsSignatureDefaultsAndNullPresenceSemantics() {
        val reference = TestMarykModel.ref { listOfString }
        for (stored in listOf(0, Unit, emptyList<String>())) {
            assertTrue(matchesFilter(Exists(reference), valueMatcher = { actual, matcher -> assertSame(reference, actual); matcher(stored) }))
        }
        assertFalse(matchesFilter(Exists(reference), valueMatcher = { _, matcher -> matcher(null) }))
        assertTrue(matchesFilter(
            And(Exists(reference), Matches("search" with "token")),
            { _, matcher -> matcher(0) },
            { _, value -> value },
            { name, value -> name == "search" && value == "token" },
            { _, _ -> false },
            { _, _ -> false },
        ))
    }
}
