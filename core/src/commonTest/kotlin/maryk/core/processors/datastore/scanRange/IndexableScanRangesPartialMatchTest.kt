package maryk.core.processors.datastore.scanRange

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.filters.And
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.Prefix
import maryk.core.query.pairs.with
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexableScanRangesPartialMatchTest {
    @Test
    fun doesNotApplyLaterGreaterThanToRangeAfterPrefix() {
        val keyRange = PrefixIndexedModel.createScanRange(filter = null, startKey = null)
        val index = PrefixIndexedModel.Meta.indexes!!.first()

        val filter = And(
            Prefix(PrefixIndexedModel { name::ref } with "Jan"),
            GreaterThan(PrefixIndexedModel { age::ref } with 10u),
        )

        val scanRange = index.createScanRange(filter, keyRange)

        assertTrue(
            scanRange.partialMatches?.any { it is IndexPartialToBeBigger && it.indexableIndex == 1 } == true
        )
        assertEquals(
            scanRange.ranges.first().end?.toList(),
            scanRange.ranges.first().start.toList(),
        )
    }
}

private object PrefixIndexedModel : RootDataModel<PrefixIndexedModel>(
    keyDefinition = {
        PrefixIndexedModel.run { id.ref() }
    },
    indexes = {
        PrefixIndexedModel.run {
            listOf(Multiple(name.ref(), age.ref()))
        }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val name by string(index = 2u)
    val age by number(index = 3u, type = UInt32)
}
