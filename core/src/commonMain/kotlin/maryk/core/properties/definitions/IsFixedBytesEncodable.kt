package maryk.core.properties.definitions

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.numeric.UInt32

/** Interface to define something can be en/decoded to fixed byte array */
interface IsFixedBytesEncodable<T: Any>: IsBytesEncodable<T> {
    /** The byte size */
    val byteSize: Int

    override fun calculateStorageByteLength(value: T) = byteSize

    companion object {
        internal fun <DO:Any> addByteSize(index: Int, definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Int) {
            definitions.add(index, "byteSize",
                NumberDefinition(type = UInt32),
                getter,
                toSerializable = { value, _ -> value?.toUInt() },
                fromSerializable = { it?.toInt() }
            )
        }
    }
}
