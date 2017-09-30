package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable
import kotlin.experimental.xor

class Reversed<T: Any>(
        val definition: IsFixedBytesEncodable<T>
) : IsFixedBytesEncodable<T> {
    override val index: Short = definition.index

    override val byteSize = definition.byteSize
    override fun <DO : Any> getValue(dataModel: DataModel<DO>, dataObject: DO) = definition.getValue(dataModel, dataObject)

    override fun convertToStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        definition.convertToStorageBytes(value, reserver) {
            writer(MAXBYTE xor it)
        }
    }

    override fun convertFromStorageBytes(length: Int, reader: () -> Byte): T {
        return definition.convertFromStorageBytes(byteSize) {
            MAXBYTE xor reader()
        }
    }
}