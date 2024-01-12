package maryk.datastore.hbase.helpers

import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToRegexMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList

/**
 * Create Filters on row key based on found partial matches
 */
fun KeyScanRanges.createPartialsFilter(): Filter? {
    return partialMatches?.let {
        return FilterList(FilterList.Operator.MUST_PASS_ALL, it.map { partialMatch ->
            partialMatch.createFilter()
        })
    }
}

private fun IsIndexPartialToMatch.createFilter(): Filter {
    when (this) {
        is IndexPartialSizeToMatch -> TODO()
        is IndexPartialToBeBigger -> TODO()
        is IndexPartialToBeOneOf -> TODO()
        is IndexPartialToBeSmaller -> TODO()
        is IndexPartialToMatch -> TODO()
        is IndexPartialToRegexMatch -> TODO()
    }
}
