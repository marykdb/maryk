package maryk.datastore.hbase.helpers

import maryk.core.processors.datastore.matchers.IndexPartialSizeToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToBeBigger
import maryk.core.processors.datastore.matchers.IndexPartialToBeOneOf
import maryk.core.processors.datastore.matchers.IndexPartialToBeSmaller
import maryk.core.processors.datastore.matchers.IndexPartialToMatch
import maryk.core.processors.datastore.matchers.IndexPartialToRegexMatch
import maryk.core.processors.datastore.matchers.IsIndexPartialToMatch
import maryk.core.processors.datastore.scanRange.ScanRanges
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.filter.BinaryComponentComparator
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.QualifierFilter

/**
 * Create Filters on row key based on found partial matches
 */
fun ScanRanges.createPartialsQualifierFilter(): Filter? {
    return partialMatches?.let {
        return FilterList(FilterList.Operator.MUST_PASS_ALL, it.mapNotNull { partialMatch ->
            partialMatch.createQualifierFilter()
        }).let { filter -> if(filter.size() > 0) filter else null }
    }
}

private fun IsIndexPartialToMatch.createQualifierFilter(): Filter? {
    return when (this) {
        is IndexPartialSizeToMatch -> {
            // There is no way with default HBase filters to match on size of row key
            null
        }
        is IndexPartialToBeBigger -> {
            if (this.fromByteIndex != null) {
                QualifierFilter(
                    if (this.inclusive) CompareOperator.GREATER_OR_EQUAL else CompareOperator.GREATER,
                    BinaryComponentComparator(this.toBeSmaller, this.fromByteIndex!!)
                )
            } else null
        }
        is IndexPartialToBeOneOf -> {
            if (this.fromByteIndex != null) {
                FilterList(
                    FilterList.Operator.MUST_PASS_ONE,
                    this.toBeOneOf.map {
                        QualifierFilter(
                            CompareOperator.EQUAL,
                            BinaryComponentComparator(it, this.fromByteIndex ?: 0)
                        )
                    }
                )
            } else null
        }
        is IndexPartialToBeSmaller -> {
            if (this.fromByteIndex != null) {
                QualifierFilter(
                    if (this.inclusive) CompareOperator.LESS_OR_EQUAL else CompareOperator.LESS,
                    BinaryComponentComparator(this.toBeBigger, this.fromByteIndex!!)
                )
            } else null
        }
        is IndexPartialToMatch -> {
            if (this.fromByteIndex != 0) {
                QualifierFilter(
                    CompareOperator.EQUAL,
                    BinaryComponentComparator(this.toMatch, this.fromByteIndex!!)
                )
            } else null
        }
        is IndexPartialToRegexMatch -> {
            // There is no way with default HBase filters to match on size of row key
            null
        }
    }
}
