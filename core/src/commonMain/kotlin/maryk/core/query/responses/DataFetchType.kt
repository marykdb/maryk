package maryk.core.query.responses

import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.DESC

sealed class DataFetchType

class FetchByTableScan(
    val direction: Direction,
    startKey: ByteArray?,
    stopKey: ByteArray?,
): DataFetchType() {
    private val startKeyBytes = startKey?.copyOf()
    private val stopKeyBytes = stopKey?.copyOf()

    val startKey: ByteArray? get() = startKeyBytes?.copyOf()
    val stopKey: ByteArray? get() = stopKeyBytes?.copyOf()

    operator fun component1() = direction
    operator fun component2(): ByteArray? = startKey
    operator fun component3(): ByteArray? = stopKey

    fun copy(
        direction: Direction = this.direction,
        startKey: ByteArray? = this.startKey,
        stopKey: ByteArray? = this.stopKey,
    ) = FetchByTableScan(direction, startKey, stopKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByTableScan) return false

        if (direction != other.direction) return false
        if (!startKeyBytes.contentEquals(other.startKeyBytes)) return false
        if (!stopKeyBytes.contentEquals(other.stopKeyBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + (startKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (stopKeyBytes?.contentHashCode() ?: 0)
        return result
    }

    override fun toString() =
        "FetchByTableScan(direction=$direction, startKey=${startKeyBytes?.contentToString()}, stopKey=${stopKeyBytes?.contentToString()})"
}

class FetchByIndexScan(
    index: ByteArray?,
    val direction: Direction,
    startKey: ByteArray?,
    stopKey: ByteArray?,
): DataFetchType() {
    private val indexBytes = index?.copyOf()
    private val startKeyBytes = startKey?.copyOf()
    private val stopKeyBytes = stopKey?.copyOf()

    val index: ByteArray? get() = indexBytes?.copyOf()
    val startKey: ByteArray? get() = startKeyBytes?.copyOf()
    val stopKey: ByteArray? get() = stopKeyBytes?.copyOf()

    operator fun component1(): ByteArray? = index
    operator fun component2() = direction
    operator fun component3(): ByteArray? = startKey
    operator fun component4(): ByteArray? = stopKey

    fun copy(
        index: ByteArray? = this.index,
        direction: Direction = this.direction,
        startKey: ByteArray? = this.startKey,
        stopKey: ByteArray? = this.stopKey,
    ) = FetchByIndexScan(index, direction, startKey, stopKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByIndexScan) return false

        if (!indexBytes.contentEquals(other.indexBytes)) return false
        if (direction != other.direction) return false
        if (!startKeyBytes.contentEquals(other.startKeyBytes)) return false
        if (!stopKeyBytes.contentEquals(other.stopKeyBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indexBytes?.contentHashCode() ?: 0
        result = 31 * result + direction.hashCode()
        result = 31 * result + (startKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (stopKeyBytes?.contentHashCode() ?: 0)
        return result
    }

    override fun toString() =
        "FetchByIndexScan(index=${indexBytes?.contentToString()}, direction=$direction, startKey=${startKeyBytes?.contentToString()}, stopKey=${stopKeyBytes?.contentToString()})"
}

object FetchByKey: DataFetchType()

data class FetchByUpdateHistoryIndex(
    val direction: Direction = DESC,
): DataFetchType()

class FetchByUniqueKey(
    uniqueIndex: ByteArray,
): DataFetchType() {
    private val uniqueIndexBytes = uniqueIndex.copyOf()

    val uniqueIndex: ByteArray get() = uniqueIndexBytes.copyOf()

    operator fun component1(): ByteArray = uniqueIndex

    fun copy(
        uniqueIndex: ByteArray = this.uniqueIndex,
    ) = FetchByUniqueKey(uniqueIndex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FetchByUniqueKey) return false
        if (!uniqueIndexBytes.contentEquals(other.uniqueIndexBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        return uniqueIndexBytes.contentHashCode()
    }

    override fun toString() =
        "FetchByUniqueKey(uniqueIndex=${uniqueIndexBytes.contentToString()})"
}
