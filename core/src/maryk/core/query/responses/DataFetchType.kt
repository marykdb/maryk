package maryk.core.query.responses

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.Bytes
import maryk.core.query.orders.Direction
import maryk.core.query.orders.Direction.DESC
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues
import maryk.json.MapType

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

    internal companion object : SimpleQueryModel<FetchByTableScan>() {
        val direction by enum(1u, FetchByTableScan::direction, Direction)
        val startKey by byteArray(2u, FetchByTableScan::startKey, required = false)
        val stopKey by byteArray(3u, FetchByTableScan::stopKey, required = false)

        override fun invoke(values: SimpleObjectValues<FetchByTableScan>) = FetchByTableScan(
            direction = values(direction.index),
            startKey = values(startKey.index),
            stopKey = values(stopKey.index),
        )
    }
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

    internal companion object : SimpleQueryModel<FetchByIndexScan>() {
        val index by byteArray(1u, FetchByIndexScan::index, required = false)
        val direction by enum(2u, FetchByIndexScan::direction, Direction)
        val startKey by byteArray(3u, FetchByIndexScan::startKey, required = false)
        val stopKey by byteArray(4u, FetchByIndexScan::stopKey, required = false)

        override fun invoke(values: SimpleObjectValues<FetchByIndexScan>) = FetchByIndexScan(
            index = values(index.index),
            direction = values(direction.index),
            startKey = values(startKey.index),
            stopKey = values(stopKey.index),
        )
    }
}

object FetchByKey: DataFetchType() {
    internal object Model : SimpleQueryModel<FetchByKey>() {
        override fun invoke(values: SimpleObjectValues<FetchByKey>) = FetchByKey
    }
}

data class FetchByUpdateHistoryIndex(
    val direction: Direction = DESC,
): DataFetchType() {
    internal companion object : SimpleQueryModel<FetchByUpdateHistoryIndex>() {
        val direction by enum(1u, FetchByUpdateHistoryIndex::direction, Direction, default = DESC)

        override fun invoke(values: SimpleObjectValues<FetchByUpdateHistoryIndex>) =
            FetchByUpdateHistoryIndex(values(direction.index))
    }
}

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

    internal companion object : SimpleQueryModel<FetchByUniqueKey>() {
        val uniqueIndex by byteArray(1u, FetchByUniqueKey::uniqueIndex)

        override fun invoke(values: SimpleObjectValues<FetchByUniqueKey>) =
            FetchByUniqueKey(values(uniqueIndex.index))
    }
}

/** Wire type for the scan strategy metadata returned with data responses. */
internal sealed class DataFetchTypeType(
    index: UInt,
    override val name: String,
    dataModel: IsObjectDataModel<out DataFetchType>,
) : IndexedEnumImpl<DataFetchTypeType>(index),
    MapType,
    IsCoreEnum,
    MultiTypeEnum<DataFetchType> {

    @Suppress("UNCHECKED_CAST")
    override val definition = EmbeddedObjectDefinition(
        dataModel = { dataModel as IsTypedObjectDataModel<DataFetchType, *, RequestContext, RequestContext> }
    )

    object TableScan : DataFetchTypeType(1u, "TableScan", FetchByTableScan)
    object IndexScan : DataFetchTypeType(2u, "IndexScan", FetchByIndexScan)
    object Key : DataFetchTypeType(3u, "Key", FetchByKey.Model)
    object UpdateHistoryIndex : DataFetchTypeType(4u, "UpdateHistoryIndex", FetchByUpdateHistoryIndex)
    object UniqueKey : DataFetchTypeType(5u, "UniqueKey", FetchByUniqueKey)

    companion object : MultiTypeEnumDefinition<DataFetchTypeType>(
        DataFetchTypeType::class,
        { listOf(TableScan, IndexScan, Key, UpdateHistoryIndex, UniqueKey) }
    )
}

internal val DataFetchType.type: DataFetchTypeType
    get() = when (this) {
        is FetchByTableScan -> DataFetchTypeType.TableScan
        is FetchByIndexScan -> DataFetchTypeType.IndexScan
        FetchByKey -> DataFetchTypeType.Key
        is FetchByUpdateHistoryIndex -> DataFetchTypeType.UpdateHistoryIndex
        is FetchByUniqueKey -> DataFetchTypeType.UniqueKey
    }

private fun <DO : Any> IsObjectDataModel<DO>.byteArray(
    index: UInt,
    getter: (DO) -> ByteArray?,
    required: Boolean = true,
) = ObjectDefinitionWrapperDelegateLoader(this) { name ->
    FlexBytesDefinitionWrapper<Bytes, ByteArray, RequestContext, FlexBytesDefinition, DO>(
        index = index,
        name = name,
        definition = FlexBytesDefinition(required = required),
        getter = getter,
        toSerializable = { value, _ -> value?.let(::Bytes) },
        fromSerializable = { it?.bytes },
    )
}
