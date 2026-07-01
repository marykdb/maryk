package maryk.datastore.indexeddb.processors

import maryk.datastore.indexeddb.createObjectRowKeyPrefix
import maryk.lib.bytes.combineToByteArray



internal fun createChangeLogRowKey(keyBytes: ByteArray, version: ULong): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), version.toBigEndianBytes())

internal fun createUpdateHistoryRowKey(version: ULong, keyBytes: ByteArray): ByteArray =
    combineToByteArray((ULong.MAX_VALUE - version).toBigEndianBytes(), keyBytes)

internal fun createHistoricSnapshotRowKey(keyBytes: ByteArray, version: ULong): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), (ULong.MAX_VALUE - version).toBigEndianBytes())

internal fun createHistoricVersionedRowKey(rowKey: ByteArray, version: ULong): ByteArray =
    combineToByteArray(rowKey, (ULong.MAX_VALUE - version).toBigEndianBytes())

internal fun createHistoricCleanupRowKey(keyBytes: ByteArray, historicRowKey: ByteArray): ByteArray =
    combineToByteArray(createObjectRowKeyPrefix(keyBytes), historicRowKey)

