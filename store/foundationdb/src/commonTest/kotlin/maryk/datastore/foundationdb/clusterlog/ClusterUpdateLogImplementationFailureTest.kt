package maryk.datastore.foundationdb.clusterlog

import maryk.core.clock.HLC
import maryk.core.models.RootDataModel
import maryk.core.models.serializers.DataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.string
import maryk.core.properties.types.Bytes
import maryk.core.values.Values
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.assertFailsWith

private const val modelId = 2u

private object ThrowingClusterModel : RootDataModel<ThrowingClusterModel>() {
    val value by string(1u)

    override val Serializer = object : DataModelSerializer<Any, Values<ThrowingClusterModel>, ThrowingClusterModel, IsPropertyContext>(this) {
        override fun readProtoBuf(length: Int, reader: () -> Byte, context: IsPropertyContext?): Values<ThrowingClusterModel> {
            throw IllegalStateException("boom")
        }
    }
}

internal class ClusterUpdateLogImplementationFailureTest {
    private fun newLog(dataModelsById: Map<UInt, maryk.core.models.IsRootDataModel> = mapOf(modelId to SimpleMarykModel)) = ClusterUpdateLog(
        logPrefix = byteArrayOf(1, 2, 3),
        consumerPrefix = byteArrayOf(4, 5, 6),
        headPrefix = byteArrayOf(7, 8, 9),
        headGroupCount = 2,
        hlcPrefix = byteArrayOf(10, 11, 12),
        hlcMaxPrefix = byteArrayOf(13, 14, 15),
        shardCount = 4,
        originId = "node-a",
        dataModelsById = dataModelsById,
        consumerId = "consumer-a",
        retention = ClusterUpdateLog.retentionDefault(),
    )

    @Test
    fun decodeValuePropagatesImplementationFailure() {
        val update = ClusterLogAddition(
            keyBytes = Bytes(ByteArray(16) { 7 }),
            version = HLC().timestamp,
            values = SimpleMarykModel.create { value with "hello" }
        )
        val encoded = newLog().encodeValue(modelId, update, SimpleMarykModel)

        assertFailsWith<IllegalStateException> {
            newLog(mapOf(modelId to ThrowingClusterModel)).decodeValue(encoded)
        }
    }
}
