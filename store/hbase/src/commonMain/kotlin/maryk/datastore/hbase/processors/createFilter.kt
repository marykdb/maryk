package maryk.datastore.hbase.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.query.filters.And
import maryk.core.query.filters.Equals
import maryk.core.query.filters.Exists
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.GreaterThanEquals
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.LessThan
import maryk.core.query.filters.LessThanEquals
import maryk.core.query.filters.Not
import maryk.core.query.filters.Or
import maryk.core.query.filters.Prefix
import maryk.core.query.filters.Range
import maryk.core.query.filters.RegEx
import maryk.core.query.filters.ValueIn
import maryk.core.query.pairs.ReferenceValuePair
import maryk.core.query.requests.IsFetchRequest
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.shared.TypeIndicator
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.RegexStringComparator
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import org.apache.hadoop.hbase.filter.SkipFilter

/**
 * Create a HBase filter based on the Maryk filter
 */
internal fun <DM : IsRootDataModel> IsFetchRequest<DM, *>.createFilter(): Filter? {
    val filter = createContentFilter(this.where)

    return if (this.filterSoftDeleted) {
        FilterList(
            listOfNotNull(
                SingleColumnValueFilter(dataColumnFamily, softDeleteIndicator, CompareOperator.NOT_EQUAL, trueIndicator),
                filter,
            )
        )
    } else {
        filter
    }
}

internal fun createContentFilter(filter: IsFilter?): Filter? {
    if (filter == null) {
        return null
    }

    return when (filter) {
        is And -> FilterList(
            FilterList.Operator.MUST_PASS_ALL,
            filter.filters.map(::createContentFilter)
        )
        is Or -> FilterList(
            FilterList.Operator.MUST_PASS_ONE,
            filter.filters.map(::createContentFilter)
        )
        is Not -> SkipFilter(
            FilterList(FilterList.Operator.MUST_PASS_ONE, filter.filters.map(::createContentFilter))
        )
        is Exists -> FilterList(
            buildList {
                filter.references.forEach {
                    val ref = it.toStorageByteArray()
                    add(SingleColumnValueFilter(dataColumnFamily, ref, CompareOperator.NOT_EQUAL, TypeIndicator.DeletedIndicator.byteArray).apply {
                        filterIfMissing = true
                    })
                }
            }
        )
        is Equals -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    add(convertToSingleColumnValueFilter(it, CompareOperator.EQUAL))
                }
            }
        )
        is LessThan -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    add(convertToSingleColumnValueFilter(it, CompareOperator.LESS))
                }
            }
        )
        is LessThanEquals -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    add(convertToSingleColumnValueFilter(it, CompareOperator.LESS_OR_EQUAL))
                }
            }
        )
        is GreaterThan -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    add(convertToSingleColumnValueFilter(it, CompareOperator.GREATER))
                }
            }
        )
        is GreaterThanEquals -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    add(convertToSingleColumnValueFilter(it, CompareOperator.GREATER_OR_EQUAL))
                }
            }
        )
        is Prefix -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach {
                    val ref = it.reference.toStorageByteArray()
                    val value = it.value

                    @Suppress("UNCHECKED_CAST")
                    val valueBytes = (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                    add(SingleColumnValueFilter(dataColumnFamily, ref, CompareOperator.EQUAL, BinaryPrefixComparator(valueBytes)).apply {
                        filterIfMissing = true
                    })
                }
            }
        )
        is Range -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach { (reference, range) ->
                    val ref = reference.toStorageByteArray()

                    @Suppress("UNCHECKED_CAST")
                    val propertyDefinition = reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>

                    val fromBytes = propertyDefinition.toStorageBytes(range.from, TypeIndicator.NoTypeIndicator.byte)
                    add(SingleColumnValueFilter(dataColumnFamily, ref, if (range.inclusiveFrom) CompareOperator.GREATER_OR_EQUAL else CompareOperator.GREATER, BinaryPrefixComparator(fromBytes)).apply {
                        filterIfMissing = true
                    })

                    val toBytes = propertyDefinition.toStorageBytes(range.to, TypeIndicator.NoTypeIndicator.byte)
                    add(SingleColumnValueFilter(dataColumnFamily, ref, if (range.inclusiveTo) CompareOperator.LESS_OR_EQUAL else CompareOperator.LESS, BinaryPrefixComparator(toBytes)).apply {
                        filterIfMissing = true
                    })
                }
            }
        )
        is RegEx -> FilterList(
            buildList {
                filter.referenceValuePairs.forEach { (reference, regex) ->
                    val ref = reference.toStorageByteArray()
                    val prefixedRegex = if (regex.pattern.startsWith("^")) {
                        regex.pattern.replace("^", "^\u0001")
                    } else "\u0001${regex.pattern}"

                    add(SingleColumnValueFilter(dataColumnFamily, ref, CompareOperator.EQUAL, RegexStringComparator(prefixedRegex)).apply {
                        filterIfMissing = true
                    })
                }
            }
        )
        is ValueIn -> FilterList(
            FilterList.Operator.MUST_PASS_ALL,
            buildList {
                filter.referenceValuePairs.map {
                    val refAsBytes = it.reference.toStorageByteArray()
                    FilterList(
                        FilterList.Operator.MUST_PASS_ONE,
                        buildList {
                            it.values.forEach { value ->
                                @Suppress("UNCHECKED_CAST")
                                val valueBytes = (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                                return SingleColumnValueFilter(dataColumnFamily, refAsBytes, CompareOperator.EQUAL, valueBytes).apply {
                                    filterIfMissing = true
                                }
                            }
                        }
                    )
                }
            }
        )
        else -> throw Exception("Unknown $filter")
    }
}

private fun convertToSingleColumnValueFilter(it: ReferenceValuePair<Any>, compareOperator: CompareOperator): SingleColumnValueFilter {
    val ref = it.reference.toStorageByteArray()
    val value = it.value

    @Suppress("UNCHECKED_CAST")
    val valueBytes = (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
    return SingleColumnValueFilter(dataColumnFamily, ref, compareOperator, valueBytes).apply {
        filterIfMissing = true
    }
}
