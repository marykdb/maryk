package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.toBytes
import maryk.core.generateUUID
import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable

object UUIDKey: IsFixedBytesEncodable<Pair<Long, Long>> {
    override val index: Short = -1
    override val byteSize = 16

    override fun <T : Any> getValue(dataModel: DataModel<T>, dataObject: T): Pair<Long, Long> {
        return generateUUID()
    }

    override fun convertToBytes(value: Pair<Long, Long>, bytes: ByteArray?, offset: Int): ByteArray {
        val (first, last) = generateUUID()
        val newBytes = bytes ?: ByteArray(byteSize)
        first.toBytes(newBytes, offset)
        last.toBytes(newBytes, offset + 8)
        return newBytes
    }

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int): Pair<Long, Long> {
        val l1 = initLong(bytes, offset)
        val l2 = initLong(bytes, offset + 8)
        return Pair(l1, l2)
    }
}
