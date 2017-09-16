package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.generateUUID
import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable

object UUIDKey: IsFixedBytesEncodable<Pair<Long, Long>> {
    override val index: Short = -1
    override val byteSize = 16

    override fun <T : Any> getValue(dataModel: DataModel<T>, dataObject: T) = generateUUID()

    override fun convertFromBytes(length: Int, reader: () -> Byte) = Pair(
        initLong(reader),
        initLong(reader)
    )

    override fun convertToBytes(value: Pair<Long, Long>, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        reserver(byteSize)
        value.first.writeBytes(writer)
        value.second.writeBytes(writer)
    }
}
