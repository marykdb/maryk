package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.objects.IsDataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue

class TypeId(
        val multiTypeDefinition: MultiTypeDefinition<*>
) : IsFixedBytesEncodable<Int> {
    override val index: Int = multiTypeDefinition.index
    override val byteSize = 2

    override fun <T : Any> getValue(dataModel: IsDataModel<T>, dataObject: T): Int {
        val multiType = dataModel.getPropertyGetter(
                multiTypeDefinition.index
        )?.invoke(dataObject) as TypedValue<*>
        return multiType.typeIndex
    }

    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) {
        (value + Short.MIN_VALUE).toShort().writeBytes(writer)
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte)
            = initShort(reader).toInt() - Short.MIN_VALUE
}