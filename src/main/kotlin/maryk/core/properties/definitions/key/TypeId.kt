package maryk.core.properties.definitions.key

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.toBytes
import maryk.core.objects.DataModel
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.types.TypedValue

class TypeId(
        val multiTypeDefinition: MultiTypeDefinition
) : IsFixedBytesEncodable<Int> {
    override val index: Short = multiTypeDefinition.index
    override val byteSize = 2

    override fun <T : Any> getValue(dataModel: DataModel<T>, dataObject: T): Int {
        val multiType = dataModel.getPropertyGetter(
                multiTypeDefinition.index
        )?.invoke(dataObject) as TypedValue<*>
        return multiType.typeIndex
    }

    override fun convertToBytes(value: Int, bytes: ByteArray?, offset: Int) = (value + Short.MIN_VALUE).toShort().toBytes(bytes, offset)

    override fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int) = initShort(bytes, offset).toInt() - Short.MIN_VALUE
}