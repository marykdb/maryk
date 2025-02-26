package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.processors.datastore.scanRange.createScanRange
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.responses.DataFetchType
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.FetchByUniqueKey
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.setTimeRange
import maryk.datastore.hbase.uniquesColumnFamily
import maryk.datastore.shared.ScanType
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.ScanType.TableScan
import maryk.datastore.shared.TypeIndicator
import maryk.datastore.shared.checkToVersion
import maryk.datastore.shared.optimizeTableScan
import maryk.datastore.shared.orderToScanType
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Result

/** Walk with [scanRequest] on [dataStore] and do [processRecord] */
internal suspend fun <DM : IsRootDataModel> processScan(
    table: AsyncTable<AdvancedScanResultConsumer>,
    scanRequest: IsScanRequest<DM, *>,
    dataStore: HbaseDataStore,
    scanSetup: ((ScanType) -> Unit)? = null,
    scanLatestUpdate: Boolean = false,
    processRecord: (Key<DM>, ULong?, Result, ByteArray?) -> Unit,
): DataFetchType {
    val keyScanRange = scanRequest.dataModel.createScanRange(scanRequest.where, scanRequest.startKey?.bytes, scanRequest.includeStart)

    scanRequest.checkToVersion(dataStore.keepAllVersions)

    when {
        // If hard key match then quit with direct record
        keyScanRange.isSingleKey() -> {
            getByKey(table, keyScanRange.ranges.first().start, scanRequest, processRecord, scanLatestUpdate)
            return FetchByKey
        }
        else -> {
            // Process uniques as a fast path
            keyScanRange.uniques?.takeIf { it.isNotEmpty() }?.let { uniqueMatchers ->
                // Only process the first unique since it has to match every found unique matcher
                // and if first is set it can go to direct key to match further
                val firstMatcher = uniqueMatchers.first()

                @Suppress("UNCHECKED_CAST")
                val value = firstMatcher.value as Comparable<Any>
                @Suppress("UNCHECKED_CAST")
                val valueBytes = (firstMatcher.definition as IsComparableDefinition<Comparable<Any>, IsPropertyContext>).toStorageBytes(value, TypeIndicator.NoTypeIndicator.byte)

                table.get(
                    Get(firstMatcher.reference).apply {
                        addColumn(uniquesColumnFamily, valueBytes)
                        readVersions(if (scanRequest is IsChangesRequest<*, *>) scanRequest.maxVersions.toInt() else 1)
                        if (scanLatestUpdate) {
                            if (scanRequest.toVersion != null) {
                                setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
                            }
                            addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray)
                        } else {
                            setTimeRange(scanRequest)
                        }
                    }
                ).let { resultFuture ->
                    val result = resultFuture.await()
                    if (!result.isEmpty) {
                        getByKey(table, result.getValue(uniquesColumnFamily, valueBytes), scanRequest, processRecord)
                    }
                }
                return FetchByUniqueKey(firstMatcher.reference)
            }

            val scanIndex = scanRequest.dataModel.orderToScanType(scanRequest.order, keyScanRange.equalPairs)

            val processedScanIndex = if (scanIndex is TableScan) {
                scanRequest.dataModel.optimizeTableScan(scanIndex, keyScanRange)
            } else scanIndex

            scanSetup?.invoke(processedScanIndex)

            return when (processedScanIndex) {
                is TableScan -> {
                    scanStore(
                        processedScanIndex,
                        table,
                        scanRequest,
                        keyScanRange,
                        scanLatestUpdate,
                        processRecord
                    )
                }
                is IndexScan -> {
                    scanIndex(
                        processedScanIndex,
                        table,
                        scanRequest,
                        keyScanRange,
                        scanLatestUpdate,
                        processRecord
                    )
                }
            }
        }
    }
}

private suspend fun <DM : IsRootDataModel> getByKey(
    table: AsyncTable<AdvancedScanResultConsumer>,
    keyBytes: ByteArray,
    scanRequest: IsScanRequest<DM, *>,
    processRecord: (Key<DM>, ULong?, Result, ByteArray?) -> Unit,
    scanLatestUpdate: Boolean = false
) {
    table.get(Get(keyBytes).apply {
        addFamily(dataColumnFamily)
        setFilter(scanRequest.createFilter())
        readVersions(if (scanRequest is IsChangesRequest<*, *>) scanRequest.maxVersions.toInt() else 1)
        if (scanLatestUpdate) {
            if (scanRequest.toVersion != null) {
                setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
            }
            addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray)
        } else {
            setTimeRange(scanRequest)
        }
    }).let { resultFuture ->
        val result = resultFuture.await()
        if (!result.isEmpty) {
            val createdVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)?.timestamp?.toULong()
            processRecord(scanRequest.dataModel.key(keyBytes), createdVersion, result, null)
        }
    }
}
