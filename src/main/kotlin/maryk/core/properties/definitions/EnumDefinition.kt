package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader

/** Definition for Enum properties */
class EnumDefinition<E : IndexedEnum<E>>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: E? = null,
        override val maxValue: E? = null,
        val values: Array<E>
) :
        IsComparableDefinition<E, IsPropertyContext>,
        IsSerializableFixedBytesEncodable<E, IsPropertyContext>,
        IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.Enum
    override val wireType = WireType.VAR_INT
    override val byteSize = 2

    private val valueByString: Map<String, E> by lazy {
        values.associate { Pair(it.name, it) }
    }

    private val valueByIndex: Map<Int, E> by lazy {
        values.associate { Pair(it.index, it) }
    }

    private fun getEnumByIndex(index: Int) = valueByIndex[index] ?: throw ParseException("Enum index does not exist $index")

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
            getEnumByIndex(initShort(reader).toInt() - Short.MIN_VALUE)

    override fun calculateStorageByteLength(value: E) = this.byteSize

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.indexAsShortToStore.writeBytes(writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?)
            = getEnumByIndex(initShortByVar(reader).toInt())

    override fun calculateTransportByteLength(value: E) = value.index.calculateVarByteLength()

    override fun writeTransportBytes(value: E, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: IsPropertyContext?)
            = value.index.writeVarBytes(writer)

    override fun asString(value: E) = value.name

    override fun fromString(string: String) =
        valueByString[string] ?: throw ParseException(string)

    /** Override equals to handle enum values comparison */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnumDefinition<*>) return false

        if (indexed != other.indexed) return false
        if (searchable != other.searchable) return false
        if (required != other.required) return false
        if (final != other.final) return false
        if (unique != other.unique) return false
        if (minValue != other.minValue && minValue?.index != other.minValue?.index) return false
        if (maxValue != other.maxValue && maxValue?.index != other.maxValue?.index) return false
        if (!areEnumsEqual(values, other.values)) return false
        if (wireType != other.wireType) return false
        if (byteSize != other.byteSize) return false

        return true
    }

    /** Override hashCode to handle enum values comparison */
    override fun hashCode(): Int {
        var result = indexed.hashCode()
        result = 31 * result + searchable.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + unique.hashCode()
        result = 31 * result + (minValue?.index?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.index?.hashCode() ?: 0)
        result = 31 * result + enumsHashCode(values)
        result = 31 * result + wireType.hashCode()
        result = 31 * result + byteSize
        return result
    }

    companion object : SimpleDataModel<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>>(
            properties = object : PropertyDefinitions<EnumDefinition<*>>() {
                init {
                    IsPropertyDefinition.addIndexed(this, EnumDefinition<*>::indexed)
                    IsPropertyDefinition.addSearchable(this, EnumDefinition<*>::searchable)
                    IsPropertyDefinition.addRequired(this, EnumDefinition<*>::required)
                    IsPropertyDefinition.addFinal(this, EnumDefinition<*>::final)
                    IsComparableDefinition.addUnique(this, EnumDefinition<*>::unique)
                    add(5, "minValue", NumberDefinition(type = UInt32)) {
                        it.minValue?.index?.toUInt32()
                    }
                    add(6, "maxValue", NumberDefinition(type = UInt32)) {
                        it.maxValue?.index?.toUInt32()
                    }
                    add(7, "values", MapDefinition(
                            keyDefinition = NumberDefinition(type = UInt32),
                            valueDefinition = StringDefinition()
                    )) { it.values.map { Pair(it.index.toUInt32(), it.name) }.toMap() }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>): EnumDefinition<IndexedEnum<Any>> {
            val valueMap = (map[7] as Map<UInt32, String>).map {
                Pair(it.key, IndexedEnum(it.key.toInt(), it.value))
            }.toMap()

            return EnumDefinition(
                    indexed = map[0] as Boolean,
                    searchable = map[1] as Boolean,
                    required = map[2] as Boolean,
                    final = map[3] as Boolean,
                    unique = map[4] as Boolean,
                    minValue = map[5]?.let{
                        valueMap[map[5] as UInt32] as IndexedEnum<Any>
                    },
                    maxValue = map[6]?.let{
                        valueMap[map[6] as UInt32] as IndexedEnum<Any>
                    },
                    values = valueMap.values.toTypedArray() as Array<IndexedEnum<Any>>
            )
        }
    }
}

private fun areEnumsEqual(enumValues: Array<out IndexedEnum<*>>, otherValues: Array<out IndexedEnum<*>>) = when {
    enumValues === otherValues -> true
    otherValues.size != enumValues.size -> false
    else -> {
        enumValues.forEachIndexed { index, item ->
            if (item.index != otherValues[index].index) {
                return false
            }
        }

        true
    }
}

private fun enumsHashCode(enumValues: Array<out IndexedEnum<*>>): Int {
    var result = 1
    enumValues.forEach { result = 31 * result + it.index.hashCode() }
    return result
}