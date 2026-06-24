package maryk.core.processors.datastore.matchers

import kotlinx.datetime.LocalTime
import maryk.core.properties.definitions.index.Multiple
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.test.models.CompleteMarykModel
import maryk.test.models.CompleteMarykModel.number
import maryk.test.models.CompleteMarykModel.string
import maryk.test.models.CompleteMarykModel.time
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConvertFilterToIndexPartsToMatchTest {
    private val nestedIndexable = Multiple(
        Multiple(
            CompleteMarykModel.string.ref(),
            CompleteMarykModel.number.ref()
        ),
        CompleteMarykModel.time.ref()
    )

    @Test
    fun nestedMultipleFindsInnerReference() {
        val indexParts = mutableListOf<IsIndexPartialToMatch>()

        convertFilterToIndexPartsToMatch(
            nestedIndexable,
            CompleteMarykModel.Meta.keyByteSize,
            null,
            Equals(CompleteMarykModel { number::ref } with 5u),
            indexParts
        )

        val match = assertIs<IndexPartialToMatch>(indexParts.single())
        assertEquals(1, match.indexableIndex)
        assertEquals(3, match.indexPartCount)
    }

    @Test
    fun nestedMultipleUsesCumulativePartOffsetForLaterReference() {
        val indexParts = mutableListOf<IsIndexPartialToMatch>()

        convertFilterToIndexPartsToMatch(
            nestedIndexable,
            CompleteMarykModel.Meta.keyByteSize,
            null,
            Equals(CompleteMarykModel { time::ref } with LocalTime(11, 10, 9)),
            indexParts
        )

        val match = assertIs<IndexPartialToMatch>(indexParts.single())
        assertEquals(2, match.indexableIndex)
        assertEquals(3, match.indexPartCount)
    }
}
