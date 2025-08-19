package maryk.datastore.hbase.processors

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.future.await
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.values.IsValuesGetter
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.readValue
import maryk.datastore.hbase.helpers.toFamilyName
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.hbase.trueIndicator
import maryk.datastore.hbase.uniquesColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.updates.IsUpdateAction
import maryk.datastore.shared.updates.Update
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.QualifierFilter

internal suspend fun <DM : IsRootDataModel> processDelete(
    table: AsyncTable<AdvancedScanResultConsumer>,
    dataModel: DM,
    uniqueReferences: List<ByteArray>,
    key: Key<DM>,
    version: HLC,
    dbIndex: UInt,
    hardDelete: Boolean,
    cache: Cache,
    updateSharedFlow: MutableSharedFlow<IsUpdateAction>
): IsDeleteResponseStatus<DM> = try {
    val exists = table.exists(Get(key.bytes)).await()

    when {
        exists -> {
            // Create version bytes
            val versionBytes = HLC.toStorageBytes(version)
            val indexDeletes = mutableListOf<Mutation>()

            val currentValues = table.get(Get(key.bytes).apply {
                addFamily(dataColumnFamily)

                val orFilters = buildList<Filter> {
                    uniqueReferences.forEach {
                        this += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(it))
                    }

                    dataModel.Meta.indexes?.forEach { indexable ->
                        if (indexable is Multiple) {
                            indexable.references.forEach {
                                it.toQualifierStorageByteArray()
                                this += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(it.toQualifierStorageByteArray()))
                            }
                        } else if (indexable is IsIndexablePropertyReference<*>) {
                            this += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(indexable.toQualifierStorageByteArray()))
                        }
                    }
                }

                if (orFilters.isNotEmpty()) {
                    filter = FilterList(FilterList.Operator.MUST_PASS_ONE, orFilters)
                }
            }).await()

            val valuesGetter = object : IsValuesGetter {
                @Suppress("UNCHECKED_CAST")
                override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? =
                    currentValues.getColumnLatestCell(dataColumnFamily, propertyReference.toStorageByteArray())?.readValue(propertyReference.comparablePropertyDefinition) as T?
            }

            uniqueReferences.forEach { uniqueReference ->
                val uniqueValue = currentValues.getValue(dataColumnFamily, uniqueReference)
                if (uniqueValue != null) {
                    indexDeletes.add(
                        Put(uniqueReference).setTimestamp(version.timestamp.toLong()).addColumn(uniquesColumnFamily, uniqueValue, softDeleteIndicator)
                    )
                }
            }

            // Delete indexed values
            dataModel.Meta.indexes?.let { indexes ->
                indexes.forEach { indexable ->
                    val valueBytes = indexable.toStorageByteArrayForIndex(valuesGetter)
                        ?: return@forEach // skip if no complete values to index are found

                    indexDeletes.add(
                        Put(valueBytes).setTimestamp(version.timestamp.toLong()).addColumn(indexable.toFamilyName(), key.bytes, softDeleteIndicator)
                    )
                }
            }

            if (hardDelete) {
                cache.delete(dbIndex, key)

                val delete = Delete(key.bytes).setTimestamp(version.timestamp.toLong())

                table.batchAll<Any>(indexDeletes + delete).await()
            } else {
                val put = Put(key.bytes)

                put.addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray, version.timestamp.toLong(), versionBytes)
                put.addColumn(dataColumnFamily, softDeleteIndicator, version.timestamp.toLong(), trueIndicator)

                table.batchAll<Any>(indexDeletes + put).await()
            }

            updateSharedFlow.emit(
                Update.Deletion(dataModel, key, version.timestamp, hardDelete)
            )

            DeleteSuccess(version.timestamp)
        }
        else -> DoesNotExist(key)
    }
} catch (e: Throwable) {
    ServerFail(e.toString(), e)
}
