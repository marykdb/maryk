package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition

interface IsIndexedEnumDefinition<E: IndexedEnum>:
    MarykPrimitive,
    IsPropertyDefinition<E>,
    IsFixedStorageBytesEncodable<E> {
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?

    val cases: () -> Array<E>

    override fun getEmbeddedByName(name: String): Nothing? = null
    override fun getEmbeddedByIndex(index: UInt): Nothing? = null

    /** Check the enum values */
    fun check() {
        this.reservedIndices?.let {
            this.cases.invoke().forEach { case ->
                require(!(reservedIndices?.contains(case.index) ?: false)) {
                    "Enum $name has ${case.index} defined in option ${case.name} while it is reserved"
                }
            }
        }
        this.reservedNames?.let {
            this.cases.invoke().forEach { case ->
                require(!(reservedNames?.contains(case.name) ?: false)) {
                    "Enum $name has a reserved name defined ${case.name}"
                }
            }
        }
    }

    /** Get Enum value by [index] */
    fun resolve(index: UInt): E?

    /** Get Enum value by [name] */
    fun resolve(name: String): E?

    override fun readStorageBytes(length: Int, reader: () -> Byte): E {
        val index = initUInt(reader, 2)
        return resolve(index)
            ?: throw DefNotFoundException("Unknown index $index for $name")
    }

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.index.writeBytes(writer, 2)
    }
}
