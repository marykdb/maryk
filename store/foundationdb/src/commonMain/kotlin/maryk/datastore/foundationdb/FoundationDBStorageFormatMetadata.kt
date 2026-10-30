package maryk.datastore.foundationdb

import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

private const val MetadataChunkSize = 100_000
private val MetadataChunkMarker = byteArrayOf(-1, 77, 75, 83, 70, 67, 72, 1)
private val StagedRowPrefix = "maryk.storage/stage/".encodeToByteArray()

/** Stage entries include two keys as well as a valid, potentially full-sized FDB value. */
internal class FoundationDBStorageFormatMetadata(private val chunkPrefix: ByteArray) {
    fun read(transaction: Transaction, key: ByteArray, value: ByteArray?): ByteArray? {
        if (!key.isStagedRow() || value == null || value.size != MetadataChunkMarker.size + 4 ||
            !MetadataChunkMarker.indices.all { value[it] == MetadataChunkMarker[it] }) return value
        var offset = MetadataChunkMarker.size
        val size = initUInt(reader = { value[offset++] }).toInt()
        require(size in MetadataChunkSize + 1..2 * MetadataChunkSize) { "Invalid chunked storage upgrade metadata length" }
        val result = ByteArray(size)
        val prefix = chunksFor(key)
        offset = 0
        var index = 0u
        while (offset < size) {
            val chunk = transaction.get(prefix + index.bytes()).awaitResult()
                ?: error("Missing storage upgrade metadata chunk")
            val expected = minOf(MetadataChunkSize, size - offset)
            require(chunk.size == expected) { "Invalid storage upgrade metadata chunk length" }
            chunk.copyInto(result, offset)
            offset += chunk.size
            index++
        }
        return result
    }

    fun write(transaction: Transaction, prefix: ByteArray, key: ByteArray, value: ByteArray?) {
        val chunks = chunksFor(key)
        transaction.clear(Range.startsWith(chunks))
        if (value == null) {
            transaction.clear(prefix + key)
        } else if (value.size <= MetadataChunkSize) {
            transaction.set(prefix + key, value)
        } else {
            require(key.isStagedRow()) { "Only staged storage rows can use chunked metadata" }
            require(value.size <= 2 * MetadataChunkSize) { "Storage upgrade metadata exceeds its chunk limit" }
            require(chunks.size + 4 <= 10_000) { "Storage upgrade metadata chunk key exceeds FoundationDB's key limit" }
            var offset = 0
            var index = 0u
            while (offset < value.size) {
                val end = minOf(offset + MetadataChunkSize, value.size)
                transaction.set(chunks + index.bytes(), value.copyOfRange(offset, end))
                offset = end
                index++
            }
            transaction.set(prefix + key, MetadataChunkMarker + value.size.toUInt().bytes())
        }
    }

    fun clear(transaction: Transaction) = transaction.clear(Range.startsWith(chunkPrefix))

    private fun chunksFor(key: ByteArray) = chunkPrefix + key.size.toUInt().bytes() + key
}

private fun ByteArray.isStagedRow() = size == StagedRowPrefix.size + 8 &&
    StagedRowPrefix.indices.all { this[it] == StagedRowPrefix[it] }

private fun UInt.bytes() = ByteArray(4).also { bytes ->
    var offset = 0
    writeBytes(writer = { bytes[offset++] = it })
}
