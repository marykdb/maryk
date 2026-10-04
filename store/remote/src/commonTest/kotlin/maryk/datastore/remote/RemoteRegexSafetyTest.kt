package maryk.datastore.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteRegexSafetyTest {
    @Test
    fun acceptsExistingPrefixQueriesAndSingleCharacterRepetitions() {
        listOf("^h.*$", "^garcia.*$", "^smi.*$", "[a-z]+", "[^a-z]?", "\\d+", "a\\.b", "\\[x\\]", "", "^$").forEach {
            assertTrue(Regex(it).isSafeRemoteRegex(), it)
        }
    }

    @Test
    fun rejectsCombinatorialAndHiddenRepetitions() {
        listOf(
            "(a|aa)+", "(a+)+$", "a*a*a*b", "a?a?", ".*?", "a{1000000000}",
            "(?i)a", "(?=a)a", "(a)\\1", "\\Q(a+)\\E", "\\p{L}+", "\\u0061+", "\\bword",
            "[a-z&&[^b]]+", "[a[b]]+", "a^", "$" + "a", "a".repeat(257),
        ).forEach {
            assertFalse(Regex(it).isSafeRemoteRegex(), it)
        }
    }
}
