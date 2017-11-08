package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.MAXBYTE
import maryk.core.objects.IsDataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable
import kotlin.experimental.xor

class Reversed<T: Any>(
        val definition: IsFixedBytesEncodable<T>
) : IsFixedBytesEncodable<T> {
    override val index: Int = definition.index

    override val byteSize = definition.byteSize
    override fun <DO : Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO) = definition.getValue(dataModel, dataObject)

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) {
        definition.writeStorageBytes(value, {
            writer(MAXBYTE xor it)
        })
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte): T {
        return definition.readStorageBytes(byteSize, {
            MAXBYTE xor reader()
        })
    }
}