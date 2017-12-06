package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.generateUUID
import maryk.core.objects.IsDataModel
import maryk.core.properties.definitions.IsFixedBytesProperty

object UUIDKey: IsFixedBytesProperty<Pair<Long, Long>> {
    override val byteSize = 16

    override fun <T : Any> getValue(dataModel: IsDataModel<T>, dataObject: T) = generateUUID()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Pair(
        initLong(reader),
        initLong(reader)
    )

    override fun writeStorageBytes(value: Pair<Long, Long>, writer: (byte: Byte) -> Unit) {
        value.first.writeBytes(writer)
        value.second.writeBytes(writer)
    }
}
