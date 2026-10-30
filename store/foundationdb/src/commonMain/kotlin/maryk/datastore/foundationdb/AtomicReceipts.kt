package maryk.datastore.foundationdb

import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.core.exceptions.StorageException
import maryk.datastore.shared.MAX_ATOMIC_BYTES
import maryk.datastore.shared.tagReleasedTransactionReceipt
import maryk.foundationdb.Transaction
import maryk.foundationdb.tuple.Tuple

// Stay comfortably below FoundationDB's 100000-byte single-value ceiling.
private const val RECEIPT_CHUNK_BYTES = 80_000
private val releasedReceiptHeader = tagReleasedTransactionReceipt(byteArrayOf())
private val maxReceiptBytes = MAX_ATOMIC_BYTES + 32 + releasedReceiptHeader.size

internal fun readAtomicReceipt(transaction: Transaction, prefix: ByteArray, id: String): ByteArray? {
    val manifest = transaction.get(prefix + Tuple.from(id).pack()).awaitResult() ?: return null
    val released = manifest.size == releasedReceiptHeader.size + 4 && releasedReceiptHeader.indices.all { manifest[it] == releasedReceiptHeader[it] }
    if (manifest.size != 4 && !released) throw StorageException("Invalid atomic receipt manifest")
    val length = manifest.takeLast(4).fold(0) { size, byte -> (size shl 8) or (byte.toInt() and 255) }
    // Tagged released manifests describe the old unframed chunks; add their
    // header exactly once when enforcing the complete receipt limit.
    if (length < 1 || length > maxReceiptBytes - if (released) releasedReceiptHeader.size else 0) {
        throw StorageException("Invalid atomic receipt length")
    }
    val receipt = ByteArray(length)
    for (offset in 0 until length step RECEIPT_CHUNK_BYTES) {
        val chunk = transaction.get(prefix + Tuple.from(id, (offset / RECEIPT_CHUNK_BYTES).toLong()).pack()).awaitResult()
            ?: throw StorageException("Missing atomic receipt chunk")
        if (chunk.size != minOf(RECEIPT_CHUNK_BYTES, length - offset)) throw StorageException("Invalid atomic receipt chunk length")
        chunk.copyInto(receipt, offset)
    }
    return if (released) releasedReceiptHeader + receipt else receipt
}

internal fun writeAtomicReceipt(transaction: Transaction, prefix: ByteArray, id: String, receipt: ByteArray) {
    // Manifest, chunks, data and history are committed in the same native transaction.
    for (offset in receipt.indices step RECEIPT_CHUNK_BYTES) {
        transaction.set(prefix + Tuple.from(id, (offset / RECEIPT_CHUNK_BYTES).toLong()).pack(),
            receipt.copyOfRange(offset, minOf(offset + RECEIPT_CHUNK_BYTES, receipt.size)))
    }
    transaction.set(prefix + Tuple.from(id).pack(), ByteArray(4) { i -> (receipt.size ushr (24 - i * 8)).toByte() })
}
