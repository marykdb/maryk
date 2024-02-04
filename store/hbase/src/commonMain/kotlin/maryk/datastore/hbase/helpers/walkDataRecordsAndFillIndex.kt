package maryk.datastore.hbase.helpers

import kotlinx.coroutines.future.await
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.IsValuesGetter
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.model.createFamilyDescriptor
import maryk.datastore.hbase.trueIndicator
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.Filter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.QualifierFilter
import kotlin.math.max

/**
 * Walks all existing data records of model in [table]
 * Will index any [indicesToIndex] with relevant values
 * Indices are already created as column families so only needed to retrieve the data and fill the index
 *
 * Currently only the latest values are indexed and not the full history.
 */
internal suspend fun walkDataRecordsAndFillIndex(
    admin: AsyncAdmin,
    table: AsyncTable<AdvancedScanResultConsumer>,
    keepAllVersions: Boolean,
    indicesToIndex: List<IsIndexable>
) {
    // First create any missing indices
    admin.disableTable(table.name).await()
    indicesToIndex.forEach { indexable ->
        val familyName = indexable.toFamilyName()
        admin.addColumnFamily(table.name, createFamilyDescriptor(familyName, keepAllVersions)).await()
    }
    admin.enableTable(table.name).await()

    // Now walk all records and fill the indices
    val orFilters = mutableListOf<Filter>(
        QualifierFilter(CompareOperator.EQUAL, BinaryComparator(MetaColumns.CreatedVersion.byteArray))
    )

    indicesToIndex.forEach { indexable ->
        if (indexable is Multiple) {
            indexable.references.forEach {
                orFilters += QualifierFilter(CompareOperator.EQUAL, BinaryComparator(it.toQualifierStorageByteArray()))
            }
        } else if (indexable is IsIndexablePropertyReference<*>) {
            orFilters += QualifierFilter(
                CompareOperator.EQUAL,
                BinaryComparator(indexable.toQualifierStorageByteArray())
            )
        }
    }

    table.getScanner(
        Scan().apply {
            addFamily(dataColumnFamily)
            setFilter(FilterList(FilterList.Operator.MUST_PASS_ONE, orFilters))
            readVersions(1)
        }
    ).use { scanner ->
        var fetchMore = true
        while (fetchMore) {
            val results = scanner.next(500)
            val indexPuts = mutableListOf<Put>()
            for (result in results) {
                for (index in indicesToIndex) {
                    var latestVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)!!.timestamp.toULong()

                    val currentValuesGetter = object : IsValuesGetter {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
                            val latestCell = result.getColumnLatestCell(dataColumnFamily, propertyReference.toStorageByteArray())
                            latestVersion = max(latestVersion, latestCell?.timestamp?.toULong() ?: 0u)
                            return latestCell?.readValue(propertyReference.propertyDefinition) as T?
                        }
                    }

                    val family = index.toFamilyName()
                    val value = index.toStorageByteArrayForIndex(currentValuesGetter)

                    indexPuts += Put(value).setTimestamp(latestVersion.toLong()).addColumn(family, result.row, trueIndicator)
                }
            }
            if (indexPuts.isNotEmpty()) {
                table.putAll(indexPuts).await()
            }
            fetchMore = results.isNotEmpty()
        }
    }
}
