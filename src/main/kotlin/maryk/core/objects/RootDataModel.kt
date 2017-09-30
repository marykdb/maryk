package maryk.core.objects

import maryk.core.bytes.Base64
import maryk.core.extensions.bytes.initByteArray
import maryk.core.properties.definitions.AbstractPropertyDefinition
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.types.Key

fun definitions(vararg keys: IsFixedBytesEncodable<*>) = arrayOf(*keys)

/** DataModel which is on root level so it can be stored and thus can have a key
 * If no key is defined the datamodel will get a UUID
 */
abstract class RootDataModel<DM: Any>(
        constructor: (Map<Int, *>) -> DM,
        keyDefinitions: Array<IsFixedBytesEncodable<out Any>> = arrayOf(UUIDKey),
        definitions: List<Def<*, DM>>
) : DataModel<DM>(constructor, definitions){
    val key = KeyDefinition(*keyDefinitions)

    /** Defines the structure of the Key */
    inner class KeyDefinition(vararg val keyDefinitions: IsFixedBytesEncodable<*>) {
        val size: Int

        init {
            var totalBytes = keyDefinitions.size - 1 // Start with size of separators

            keyDefinitions.forEach {
                when {
                    it is AbstractPropertyDefinition<*> -> {
                        checkDefinition(it)
                    }
                    it is Reversed<*> && it.definition is AbstractPropertyDefinition<*> -> {
                        checkDefinition(it.definition)
                    }
                }
                totalBytes += it.byteSize
            }
            this.size = totalBytes
        }

        private fun checkDefinition(it: AbstractPropertyDefinition<*>) {
            assert(it.required, { "Definition ${it.name} should be required" })
            assert(it.final, { "Definition ${it.name} should be final" })
        }

        /** Get Key by byte array */
        fun get(bytes: ByteArray) = Key<DM>(bytes)

        /** Get Key by base64 byte representation */
        fun get(base64: String): Key<DM> = this.get(Base64.decode(base64))

        /** Get Key by byte reader */
        fun get(reader: () -> Byte): Key<DM> = Key<DM>(
                initByteArray(size, reader)
        )

        /** Get Key based on DataObject */
        fun getKey(dataObject: DM): Key<DM> {
            val bytes = ByteArray(this.size)
            var index = 0
            keyDefinitions.forEach {
                val value = it.getValue(this@RootDataModel, dataObject)

                @Suppress("UNCHECKED_CAST")
                (it as IsFixedBytesEncodable<Any>).convertToStorageBytes(value, {}) {
                    bytes[index++] = it
                }

                // Add separator
                if (index < this.size) {
                    bytes[index++] = 1
                }
            }
            return Key(bytes)
        }
    }
}
