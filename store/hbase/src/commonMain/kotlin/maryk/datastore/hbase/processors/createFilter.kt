package maryk.datastore.hbase.processors

import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.matchers.QualifierExactMatcher
import maryk.core.processors.datastore.matchers.QualifierFuzzyMatcher
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.references.TypeReference
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
import org.apache.hadoop.hbase.filter.BinaryComparator
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

internal fun createContentFilter(filter: IsFilter?, isNot: Boolean = false): Filter? {
    if (filter == null) {
        return null
    }

    return when (filter) {
        is And -> filter.filters.singleOrFilterList { createContentFilter(it, isNot) }
        is Or -> filter.filters.singleOrFilterList(FilterList.Operator.MUST_PASS_ONE) { createContentFilter(it, isNot) }
        is Not -> SkipFilter(
            filter.filters.singleOrFilterList { createContentFilter(it, !isNot) }
        )
        is Exists -> buildList {
            filter.references.forEach {
                val ref = it.toStorageByteArray()
                val operator = if (isNot) CompareOperator.EQUAL else CompareOperator.NOT_EQUAL
                add(SingleColumnValueFilter(dataColumnFamily, ref, operator, TypeIndicator.DeletedIndicator.byteArray).apply {
                    filterIfMissing = true
                })
            }
        }.singleOrFilterList()
        is Equals -> buildList {
            filter.referenceValuePairs.forEach {
                add(convertToSingleColumnValueFilter(it, if (isNot) CompareOperator.NOT_EQUAL else CompareOperator.EQUAL))
            }
        }.singleOrFilterList()
        is LessThan -> buildList {
            filter.referenceValuePairs.forEach {
                add(convertToSingleColumnValueFilter(it, if (isNot) CompareOperator.GREATER_OR_EQUAL else CompareOperator.LESS))
            }
        }.singleOrFilterList()
        is LessThanEquals -> buildList {
            filter.referenceValuePairs.forEach {
                add(convertToSingleColumnValueFilter(it, if (isNot) CompareOperator.GREATER else CompareOperator.LESS_OR_EQUAL))
            }
        }.singleOrFilterList()
        is GreaterThan -> buildList {
            filter.referenceValuePairs.forEach {
                add(convertToSingleColumnValueFilter(it, if (isNot) CompareOperator.LESS_OR_EQUAL else CompareOperator.GREATER))
            }
        }.singleOrFilterList()
        is GreaterThanEquals -> buildList {
            filter.referenceValuePairs.forEach {
                add(convertToSingleColumnValueFilter(it, if (isNot) CompareOperator.LESS else CompareOperator.GREATER_OR_EQUAL))
            }
        }.singleOrFilterList()
        is Prefix -> buildList {
            filter.referenceValuePairs.forEach {
                val matcher = it.reference.toQualifierMatcher()
                if (matcher !is QualifierExactMatcher) {
                    throw StorageException("Fuzzy filters are not supported by this storage engine yet")
                }
                val refAsBytes = matcher.qualifier
                val value = it.value

                @Suppress("UNCHECKED_CAST")
                val valueBytes = (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                add(SingleColumnValueFilter(dataColumnFamily, refAsBytes, if (isNot) CompareOperator.NOT_EQUAL else CompareOperator.EQUAL, BinaryPrefixComparator(valueBytes)).apply {
                    filterIfMissing = true
                })
            }
        }.singleOrFilterList()
        is Range -> buildList {
            filter.referenceValuePairs.forEach { (reference, range) ->
                val matcher = reference.toQualifierMatcher()
                if (matcher !is QualifierExactMatcher) {
                    throw StorageException("Fuzzy filters are not supported by this storage engine yet")
                }
                val refAsBytes = matcher.qualifier

                @Suppress("UNCHECKED_CAST")
                val propertyDefinition = reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>

                val fromBytes = propertyDefinition.toStorageBytes(range.from, TypeIndicator.NoTypeIndicator.byte)
                val fromOperator = if (isNot) { if (range.inclusiveFrom) CompareOperator.LESS else CompareOperator.LESS_OR_EQUAL } else { if (range.inclusiveFrom) CompareOperator.GREATER_OR_EQUAL else CompareOperator.GREATER }
                val fromFilter = SingleColumnValueFilter(dataColumnFamily, refAsBytes, fromOperator, BinaryPrefixComparator(fromBytes)).apply {
                    filterIfMissing = true
                }

                val toOperator = if (isNot) { if (range.inclusiveFrom) CompareOperator.GREATER else CompareOperator.GREATER_OR_EQUAL } else { if (range.inclusiveFrom) CompareOperator.LESS_OR_EQUAL else CompareOperator.LESS }
                val toBytes = propertyDefinition.toStorageBytes(range.to, TypeIndicator.NoTypeIndicator.byte)
                val toFilter = SingleColumnValueFilter(dataColumnFamily, refAsBytes, toOperator, BinaryPrefixComparator(toBytes)).apply {
                    filterIfMissing = true
                }

                if (isNot) {
                    add(FilterList(FilterList.Operator.MUST_PASS_ONE, fromFilter, toFilter))
                } else {
                    add(fromFilter)
                    add(toFilter)
                }
            }
        }.singleOrFilterList()
        is RegEx -> buildList {
            filter.referenceValuePairs.forEach { (reference, regex) ->
                val matcher = reference.toQualifierMatcher()
                if (matcher !is QualifierExactMatcher) {
                    throw StorageException("Fuzzy filters are not supported by this storage engine yet")
                }
                val refAsBytes = matcher.qualifier
                val prefixedRegex = if (regex.pattern.startsWith("^")) {
                    regex.pattern.replace("^", "^\u0001")
                } else regex.pattern

                add(SingleColumnValueFilter(dataColumnFamily, refAsBytes, if (isNot) CompareOperator.NOT_EQUAL else CompareOperator.EQUAL, RegexStringComparator(prefixedRegex)).apply {
                    filterIfMissing = true
                })
            }
        }.singleOrFilterList()
        is ValueIn -> buildList {
            addAll(filter.referenceValuePairs.mapNotNull {
                val matcher = it.reference.toQualifierMatcher()
                if (matcher !is QualifierExactMatcher) {
                    throw StorageException("Fuzzy filters are not supported by this storage engine yet")
                }
                val refAsBytes = matcher.qualifier
                buildList<Filter> {
                    it.values.forEach { value ->
                        @Suppress("UNCHECKED_CAST")
                        val valueBytes = (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                        add(SingleColumnValueFilter(dataColumnFamily, refAsBytes, if (isNot) CompareOperator.NOT_EQUAL else CompareOperator.EQUAL, valueBytes).apply {
                            filterIfMissing = true
                        })
                    }
                }.singleOrFilterList(if (isNot) FilterList.Operator.MUST_PASS_ALL else FilterList.Operator.MUST_PASS_ONE)
            })
        }.singleOrFilterList()
        else -> throw Exception("Unknown $filter")
    }
}

private fun List<Filter>.singleOrFilterList(operator: FilterList.Operator = FilterList.Operator.MUST_PASS_ALL): Filter? {
    return if (this.isEmpty()) {
        return null
    } else if (this.size == 1) {
        this.first()
    } else {
        FilterList(operator, this)
    }
}

private fun <E: IsFilter> List<E>.singleOrFilterList(operator: FilterList.Operator = FilterList.Operator.MUST_PASS_ALL, createFilter: (E?) -> Filter?): Filter? {
    return if (this.isEmpty()) {
        return null
    } else if (this.size == 1) {
        createFilter(this.first())
    } else {
        FilterList(operator, this.map(createFilter))
    }
}

private fun convertToSingleColumnValueFilter(it: ReferenceValuePair<Any>, compareOperator: CompareOperator): SingleColumnValueFilter {
    val matcher = it.reference.toQualifierMatcher()

    when (matcher) {
        is QualifierExactMatcher -> {
            val ref = matcher.qualifier
            val value = it.value

            @Suppress("UNCHECKED_CAST")
            val valueComparator = when (it.reference) {
                is TypeReference<*, *, *> -> {
                    var index = 0
                    val type = value as MultiTypeEnum<*>
                    val isSimple = value.definition is IsSimpleValueDefinition<*, *>
                    val valueBytes = ByteArray(type.index.calculateVarIntWithExtraInfoByteSize()).also {  valueBytes ->
                        type.index.writeVarIntWithExtraInfo(
                            if (isSimple) TypeIndicator.SimpleTypeIndicator.byte
                            else TypeIndicator.ComplexTypeIndicator.byte
                        ) { valueBytes[index++] = it }
                    }
                    if (isSimple) BinaryPrefixComparator(valueBytes) else BinaryComparator(valueBytes)
                }
                else -> BinaryComparator(
                    (it.reference.comparablePropertyDefinition as IsStorageBytesEncodable<Any>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)
                )
            }

            return SingleColumnValueFilter(dataColumnFamily, ref, compareOperator, valueComparator).apply {
                filterIfMissing = true
            }
        }
        is QualifierFuzzyMatcher -> throw StorageException("Fuzzy filters are not supported by this storage engine yet")
        else -> throw StorageException("Unknown matcher type")
    }
}
