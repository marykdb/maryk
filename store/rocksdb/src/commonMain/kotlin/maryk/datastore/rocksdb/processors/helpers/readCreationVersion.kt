package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.toULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.Transaction

fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> readCreationVersion(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<DM>
) = transaction.get(columnFamilies.table, readOptions, key.bytes)?.toULong()
