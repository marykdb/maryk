package maryk.core.query.responses

import maryk.core.query.orders.Direction

sealed class DataFetchType

data class FetchByTableScan(
    val direction: Direction,
    val startKey: ByteArray?,
    val stopKey: ByteArray?,
): DataFetchType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByTableScan) return false

        if (direction != other.direction) return false
        if (!startKey.contentEquals(other.startKey)) return false
        if (!stopKey.contentEquals(other.stopKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + (startKey?.contentHashCode() ?: 0)
        result = 31 * result + (stopKey?.contentHashCode() ?: 0)
        return result
    }
}

data class FetchByIndexScan(
    val index: ByteArray?,
    val direction: Direction,
    val startKey: ByteArray?,
    val stopKey: ByteArray?,
): DataFetchType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByIndexScan) return false

        if (!index.contentEquals(other.index)) return false
        if (direction != other.direction) return false
        if (!startKey.contentEquals(other.startKey)) return false
        if (!stopKey.contentEquals(other.stopKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index?.contentHashCode() ?: 0
        result = 31 * result + direction.hashCode()
        result = 31 * result + (startKey?.contentHashCode() ?: 0)
        result = 31 * result + (stopKey?.contentHashCode() ?: 0)
        return result
    }
}

object FetchByKey: DataFetchType()

data class FetchByUniqueKey(
    val uniqueIndex: ByteArray,
): DataFetchType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByUniqueKey) return false
        if (!uniqueIndex.contentEquals(other.uniqueIndex)) return false
        return true
    }

    override fun hashCode(): Int {
        return uniqueIndex.contentHashCode()
    }
}
