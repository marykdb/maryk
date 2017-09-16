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

    override fun convertToBytes(value: T, bytes: ByteArray?, offset: Int): ByteArray {
        val newBytes = definition.convertToBytes(value, bytes, offset)

        (0 until byteSize).forEach {
            newBytes[it + offset] = MAXBYTE xor newBytes[it + offset]
        }
        return newBytes
    }

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int): T {
        val newBytes = bytes.copyOfRange(offset, offset + length)

        (0 until newBytes.size).forEach {
            newBytes[it] = MAXBYTE xor newBytes[it]
        }

        return definition.convertFromBytes(newBytes, 0)
    }

    override fun convertToBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        val bytesToReverse = ByteArray(definition.byteSize)
        var index = 0
        definition.convertToBytes(value, reserver){
            bytesToReverse[index++] = it
        }

        (bytesToReverse.lastIndex .. 0).forEach {
            writer(bytesToReverse[it])
        }
    }

    override fun convertFromBytes(length: Int, reader: () -> Byte): T {
        val bytesToReverse = ByteArray(definition.byteSize)
        var index = -1
        (0 until byteSize).forEach {
            bytesToReverse[++index] = reader()
        }

        return definition.convertFromBytes(byteSize, {
            bytesToReverse[index--]
        })
    }
}