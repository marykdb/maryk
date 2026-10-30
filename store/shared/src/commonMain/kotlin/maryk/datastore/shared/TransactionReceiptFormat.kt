package maryk.datastore.shared

private val ReceiptMagic = byteArrayOf(0x4d, 0x4b, 0x54, 0x52, 0x43)
internal const val ReleasedReceiptVersion = 1
internal const val CurrentReceiptVersion = 2

/** The opening upgrade knows these opaque bytes were written with the released request codec. */
fun tagReleasedTransactionReceipt(bytes: ByteArray): ByteArray =
    ReceiptMagic + byteArrayOf(ReleasedReceiptVersion.toByte()) + bytes

internal fun tagCurrentTransactionReceipt(bytes: ByteArray): ByteArray =
    ReceiptMagic + byteArrayOf(CurrentReceiptVersion.toByte()) + bytes

internal fun transactionReceiptVersion(bytes: ByteArray): Int? =
    if (bytes.size > ReceiptMagic.size && ReceiptMagic.indices.all { bytes[it] == ReceiptMagic[it] }) {
        bytes[ReceiptMagic.size].toInt() and 255
    } else null

internal val TransactionReceiptHeaderSize get() = ReceiptMagic.size + 1

/** Both encodings are computed before entering backend transaction callbacks. */
data class TransactionFingerprints(
    val current: ByteArray,
    val released: ByteArray?,
    val releasedUnavailableReason: String? = null,
)
