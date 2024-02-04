package maryk.datastore.hbase.helpers

import kotlinx.coroutines.future.await
import maryk.core.properties.definitions.index.IsIndexable
import org.apache.hadoop.hbase.InvalidFamilyOperationException
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncAdmin

/**
 * Deletes all index values for [indexable]
 */
internal suspend fun deleteCompleteIndexContents(
    admin: AsyncAdmin,
    tableName: TableName,
    indexable: IsIndexable
) {
    val indexFamily = indexable.toFamilyName()

    admin.disableTable(tableName).await()
    try {
        admin.deleteColumnFamily(tableName, indexFamily).await()
    } catch (e: InvalidFamilyOperationException) {
        // Ignore if it does not exist
    }
    admin.enableTable(tableName).await()
}
