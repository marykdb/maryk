package maryk.core.properties.enum

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.lib.exceptions.ParseException

interface IsIndexedEnumDefinition<E: IndexedEnum>:
    MarykPrimitive,
    IsPropertyDefinition<E>,
    IsFixedStorageBytesEncodable<E>,
    IsSimpleValueDefinition<E, IsPropertyContext> {

    val name: String

    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?

    val cases: () -> List<E>

    override fun getEmbeddedByName(name: String): Nothing? = null
    override fun getEmbeddedByIndex(index: UInt): Nothing? = null

    /** Check the enum values */
    fun check() = check(this.cases.invoke())

    /** Check [cases] from a single captured enum snapshot. */
    fun check(cases: List<IndexedEnum>) {
        val indices = mutableSetOf<UInt>()
        val names = mutableSetOf<String>()
        cases.forEach { case ->
            require(indices.add(case.index)) {
                "Enum $name has duplicate index ${case.index} for option ${case.name}"
            }
            (case.alternativeNames.orEmpty() + case.name).forEach { caseName ->
                require(names.add(caseName)) {
                    "Enum $name has duplicate name $caseName"
                }
            }
        }
        this.reservedIndices?.let {
            cases.forEach { case ->
                require(reservedIndices?.contains(case.index) != true) {
                    "Enum $name has ${case.index} defined in option ${case.name} while it is reserved"
                }
            }
        }
        this.reservedNames?.let { reservedNames ->
            cases.forEach { case ->
                val reservedName = (case.alternativeNames.orEmpty() + case.name).firstOrNull(reservedNames::contains)
                require(reservedName == null) {
                    "Enum $name has a reserved name defined $reservedName"
                }
            }
        }
    }

    override fun fromString(string: String) =
        resolve(string) ?: throw ParseException(string)

    override fun asString(value: E) =
        if (value is IsCoreEnum) {
            value.name
        } else {
            "${value.name}(${value.index})"
        }

    @Suppress("UNCHECKED_CAST")
    override fun fromNativeType(value: Any): E? = value as? E

    override fun calculateTransportByteLength(value: E) = //calculateStorageByteLength(value)
         value.index.calculateVarByteLength()

    override fun writeTransportBytes(
        value: E,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        value.index.writeVarBytes(writer)
    }

    override fun calculateStorageByteLength(value: E) =
        super.calculateStorageByteLength(value)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?, earlierValue: E?): E {
        val index = initUIntByVar(reader)
        return resolve(index)
            ?: throw DefNotFoundException("Unknown index $index for $name")
    }

    /** Get Enum value by [index] */
    fun resolve(index: UInt): E?

    /** Get Enum value by [name] */
    fun resolve(name: String): E?

    override fun readStorageBytes(length: Int, reader: () -> Byte): E {
        if (length != byteSize) {
            throw ParseException("Invalid storage byte length for Enum $name: $length != $byteSize")
        }

        val index = initUInt(reader, 2)
        return resolve(index)
            ?: throw DefNotFoundException("Unknown index $index for $name")
    }

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.index.writeBytes(writer, 2)
    }
}
