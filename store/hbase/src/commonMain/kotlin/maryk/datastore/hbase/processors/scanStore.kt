package maryk.datastore.hbase.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.types.Key
import maryk.core.query.orders.Direction.ASC
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByTableScan
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.createPartialsRowKeyFilter
import maryk.datastore.hbase.helpers.setTimeRange
import maryk.datastore.shared.ScanType
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange
import org.apache.hadoop.hbase.filter.QualifierFilter

internal fun <DM : IsRootDataModel> scanStore(
    tableScan: ScanType.TableScan,
    table: AsyncTable<AdvancedScanResultConsumer>,
    scanRequest: IsScanRequest<DM, *>,
    scanRange: KeyScanRanges,
    scanLatestUpdate: Boolean,
    processStoreValue: (Key<DM>, ULong?, Result, ByteArray?) -> Unit
): DataFetchType {
    var overallStartKey: ByteArray? = null
    var overallEndKey: ByteArray? = null

    val scan = Scan().apply {
        addFamily(dataColumnFamily)
        withStartRow(scanRange.startKey, scanRange.includeStart)
        if (tableScan.direction == ASC) {
            val last = scanRange.ranges.last()
            withStopRow(last.end, last.endInclusive)

            overallStartKey = scanRange.ranges.first().getAscendingStartKey(scanRange.startKey, scanRange.includeStart)
            overallEndKey = last.getDescendingStartKey()
        } else {
            this.isReversed = true
            val last = scanRange.ranges.first()
            withStopRow(last.start, last.startInclusive)

            overallStartKey = scanRange.ranges.last().getDescendingStartKey(scanRange.startKey, scanRange.includeStart)
            overallEndKey = last.getAscendingStartKey()
        }

        val multiFilter = FilterList(FilterList.Operator.MUST_PASS_ALL)

        if (scanRange.ranges.size > 1) {
            multiFilter.addFilter(
                MultiRowRangeFilter(
                    scanRange.ranges.map { range ->
                        RowRange(range.start, range.startInclusive, range.end, range.endInclusive)
                    }
                )
            )
        }

        scanRange.createPartialsRowKeyFilter()?.let(multiFilter::addFilter)

        scanRequest.createFilter()?.let(multiFilter::addFilter)

        if (scanLatestUpdate) {
            if (scanRequest.toVersion != null) {
                setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
            }
            multiFilter.addFilter(QualifierFilter(CompareOperator.EQUAL, BinaryComparator(MetaColumns.LatestVersion.byteArray)))
        } else {
            setTimeRange(scanRequest)
        }

        setFilter(multiFilter)

        readVersions(if (scanRequest is IsChangesRequest<*, *>) scanRequest.maxVersions.toInt() else 1)

        maxResultSize = scanRequest.limit.toLong()
        caching = maxResultSize.toInt()
    }

    table.getScanner(scan).use { scanner ->
        var count = 0

        while (count < scanRequest.limit.toLong()) {
            val result = scanner.next()

            if (result == null || result.isEmpty) {
                break
            }

            val key = scanRequest.dataModel.key(result.row)

            val creationVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)?.timestamp?.toULong()

            processStoreValue(key, creationVersion, result, null)

            count++
        }
    }

    return FetchByTableScan(
        direction = tableScan.direction,
        startKey = overallStartKey,
        stopKey = overallEndKey,
    )
}
