package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.IndexableScanRanges
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByIndexScan
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.createPartialsQualifierFilter
import maryk.datastore.hbase.helpers.createPartialsRowKeyFilter
import maryk.datastore.hbase.helpers.setTimeRange
import maryk.datastore.hbase.helpers.toFamilyName
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.shared.ScanType
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength
import maryk.lib.extensions.compare.nextByteInSameLength
import maryk.lib.extensions.compare.prevByteInSameLength
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.ResultScanner
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange
import org.apache.hadoop.hbase.filter.ValueFilter

internal suspend fun <DM : IsRootDataModel> scanIndex(
    indexScan: ScanType.IndexScan,
    table: AsyncTable<AdvancedScanResultConsumer>,
    scanRequest: IsScanRequest<DM, *>,
    keyScanRange: KeyScanRanges,
    indexScanRanges: IndexableScanRanges?,
    scanLatestUpdate: Boolean,
    processStoreValue: (Key<DM>, ULong?, Result, ByteArray?) -> Unit
): DataFetchType {
    val indexScanRange = indexScanRanges ?: indexScan.index.createScanRange(scanRequest.where, keyScanRange)

    var overallStartKey: ByteArray? = null
    var overallStopKey: ByteArray? = null

    val scan = Scan().apply {
        addFamily(indexScan.index.toFamilyName())

        if (indexScan.direction == ASC) {
            val first = indexScanRange.ranges.first()
            withStartRow(first.start, first.startInclusive)

            val last = indexScanRange.ranges.last()
            if (last.endInclusive) {
                withStopRow(last.end?.nextByteInSameLength(), false)
            } else {
                withStopRow(last.end, false)
            }

            overallStartKey = first.getAscendingStartKey()
            overallStopKey = last.getDescendingStartKey()
        } else {
            this.isReversed = true
            val first = indexScanRange.ranges.last()
            if (first.endInclusive) {
                withStartRow(first.end?.nextByteInSameLength(), false)
            } else {
                withStartRow(first.end, false)
            }

            val last = indexScanRange.ranges.first()
            withStopRow(last.start, last.startInclusive)

            overallStartKey = first.getDescendingStartKey()
            overallStopKey = last.getAscendingStartKey()
        }

        val multiFilter = FilterList(FilterList.Operator.MUST_PASS_ALL)

        if (indexScanRange.ranges.size > 1) {
            multiFilter.addFilter(
                MultiRowRangeFilter(
                    indexScanRange.ranges.map { range ->
                        RowRange(
                            range.start,
                            range.startInclusive,
                            if (range.endInclusive) range.end?.nextByteInSameLength() else range.end,
                            false
                        )
                    }
                )
            )
        }

        indexScanRange.createPartialsRowKeyFilter()?.let(multiFilter::addFilter)
        indexScanRange.keyScanRange.createPartialsQualifierFilter()?.let(multiFilter::addFilter)

        // Do not add any deleted values
        multiFilter.addFilter(ValueFilter(CompareOperator.EQUAL, BinaryComparator(trueIndicator)))

        setFilter(multiFilter)

        readVersions(1)

        if (scanLatestUpdate) {
            if (scanRequest.toVersion != null) {
                setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
            }
        } else {
            setTimeRange(scanRequest)
        }

        setCaching(scanRequest.limit.toInt())
        caching = maxResultSize.toInt()
    }

    val limit = scanRequest.limit.toInt()
    var count = 0
    var shouldSkipTillStartKey = scanRequest.startKey != null

    val scanner: ResultScanner = table.getScanner(scan)
    try {
        scan@while (count < limit) {
            val results = scanner.next(limit) ?: break

            if (results.isEmpty()) {
                break
            }

            val sortingKeys = mutableListOf<ByteArray>()

            val gets = buildList {
                for (result in results) {
                    val cells = result.rawCells()
                    val range = if (indexScan.direction == ASC) {
                        cells.indices
                    } else {
                        cells.indices.reversed()
                    }
                    for (cellIndex in range) {
                        val cell = cells[cellIndex]
                        if (shouldSkipTillStartKey) {
                            if (scanRequest.startKey!!.bytes.compareToWithOffsetLength(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength) == 0) {
                                shouldSkipTillStartKey = false

                                val newRow = result.row + scanRequest.startKey!!.bytes
                                if (indexScan.direction == ASC) {
                                    if (overallStartKey == null || overallStartKey.isEmpty() || newRow > overallStartKey) {
                                        overallStartKey = if (!scanRequest.includeStart) {
                                            newRow.nextByteInSameLength()
                                        } else newRow
                                    }
                                } else {
                                    if (overallStartKey == null || overallStartKey.isEmpty() || newRow < overallStartKey) {
                                        overallStartKey = if (!scanRequest.includeStart) {
                                            newRow.prevByteInSameLength()
                                        } else newRow
                                    }
                                }

                                if (!scanRequest.includeStart) {
                                    // Skip start key as it is not included
                                    continue
                                }
                            } else {
                                continue
                            }
                        }

                        add(
                            Get(cell.qualifierArray, cell.qualifierOffset, cell.qualifierLength).apply {
                                sortingKeys.add(result.row + this.row)

                                addFamily(dataColumnFamily)
                                readVersions(if (scanRequest is IsChangesRequest<*, *>) scanRequest.maxVersions.toInt() else 1)

                                val allFilters = FilterList(FilterList.Operator.MUST_PASS_ALL)
                                keyScanRange.createPartialsRowKeyFilter()?.let(allFilters::addFilter)
                                scanRequest.createFilter()?.let(allFilters::addFilter)

                                if (allFilters.filters.isNotEmpty()) {
                                    setFilter(allFilters)
                                }

                                if (scanLatestUpdate) {
                                    if (scanRequest.toVersion != null) {
                                        setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
                                    }
                                    // Dont scan for specific column as it doesnt filter anymore.
                                    // addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray)
                                } else {
                                    setTimeRange(scanRequest)
                                }
                            }
                        )
                    }
                }
            }

            val getResults = table.getAll(gets).await()

            for ((index, result) in getResults.withIndex()) {
                if (result == null || result.isEmpty) {
                    continue
                }
                val key = scanRequest.dataModel.key(result.row)
                val creationVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)?.timestamp?.toULong()
                processStoreValue(key, creationVersion, result, sortingKeys[index])

                count++
                if (count >= limit) {
                    break
                }
            }
        }
    } finally {
        scanner.close()
    }

    return FetchByIndexScan(
        index = indexScan.index.referenceStorageByteArray.bytes,
        direction = indexScan.direction,
        startKey = overallStartKey,
        stopKey = overallStopKey,
    )
}
