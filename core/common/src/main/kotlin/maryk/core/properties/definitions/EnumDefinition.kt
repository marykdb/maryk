package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.IndexedEnumDefinition
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.DataModelContext
import maryk.lib.exceptions.ParseException

/** Definition for Enum properties */
class EnumDefinition<E : IndexedEnum<E>>(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: E? = null,
    override val maxValue: E? = null,
    override val default: E? = null,
    val enum: IndexedEnumDefinition<E>
) :
    IsComparableDefinition<E, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<E, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<E>,
    IsWithDefaultDefinition<E>
{
    override val propertyDefinitionType = PropertyDefinitionType.Enum
    override val wireType = WireType.VAR_INT
    override val byteSize = 2

    private val valueByString: Map<String, E> by lazy {
        enum.values().associate { Pair(it.name, it) }
    }

    private val valueByIndex: Map<Int, E> by lazy {
        enum.values().associate { Pair(it.index, it) }
    }

    private fun getEnumByIndex(index: Int) = valueByIndex[index] ?: throw ParseException("Enum index does not exist $index")

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        getEnumByIndex(initShort(reader).toInt() - Short.MIN_VALUE)

    override fun calculateStorageByteLength(value: E) = this.byteSize

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.indexAsShortToStore.writeBytes(writer)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) =
        getEnumByIndex(initShortByVar(reader).toInt())

    override fun calculateTransportByteLength(value: E) =
        value.index.calculateVarByteLength()

    override fun writeTransportBytes(value: E, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) =
        value.index.writeVarBytes(writer)

    override fun asString(value: E) = value.name

    override fun fromString(string: String) =
        valueByString[string] ?: throw ParseException(string)

    override fun fromNativeType(value: Any): E? = null

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
        if (default != other.default && default?.index != other.default?.index) return false
        if (enum.name != other.enum.name) return false
        if (!areEnumsEqual(enum.values(), other.enum.values())) return false
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
        result = 31 * result + (default?.index?.hashCode() ?: 0)
        result = 31 * result + enum.name.hashCode()
        result = 31 * result + enumsHashCode(enum.values())
        result = 31 * result + wireType.hashCode()
        result = 31 * result + byteSize
        return result
    }

    object Model : ContextualDataModel<EnumDefinition<*>, PropertyDefinitions<EnumDefinition<*>>, DataModelContext, EnumDefinitionContext>(
        contextTransformer = { EnumDefinitionContext() },
        properties = object : PropertyDefinitions<EnumDefinition<*>>() {
            init {
                IsPropertyDefinition.addIndexed(this, EnumDefinition<*>::indexed)
                IsPropertyDefinition.addSearchable(this, EnumDefinition<*>::searchable)
                IsPropertyDefinition.addRequired(this, EnumDefinition<*>::required)
                IsPropertyDefinition.addFinal(this, EnumDefinition<*>::final)
                IsComparableDefinition.addUnique(this, EnumDefinition<*>::unique)
                add(5, "name",
                    StringDefinition(),
                    getter = EnumDefinition<*>::enum,
                    toSerializable = { it?.name },
                    fromSerializable = { null },
                    capturer = { context: EnumDefinitionContext, value ->
                        context.name = value
                    }
                )
                @Suppress("UNCHECKED_CAST")
                add(6, "values",
                    definition = MapDefinition<UInt32, String, EnumDefinitionContext>(
                        keyDefinition = NumberDefinition(type = UInt32),
                        valueDefinition = StringDefinition()
                    ),
                    getter = EnumDefinition<*>::enum as (EnumDefinition<*>) -> IndexedEnumDefinition<IndexedEnum<Any>>?,
                    toSerializable = {
                        it?.values?.invoke()?.map {
                            Pair(it.index.toUInt32(), it.name)
                        }?.toMap()
                    },
                    fromSerializable = { null },
                    capturer = { context, value ->
                        context.values = value
                    }
                )
                @Suppress("UNCHECKED_CAST")
                add(7, "minValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: EnumDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                        }
                    ) as IsSerializableFlexBytesEncodable<IndexedEnum<*>, IsPropertyContext>,
                    getter = EnumDefinition<*>::minValue
                )
                @Suppress("UNCHECKED_CAST")
                add(8, "maxValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: EnumDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                        }
                    ) as IsSerializableFlexBytesEncodable<IndexedEnum<*>, IsPropertyContext>,
                    getter = EnumDefinition<*>::maxValue
                )
                @Suppress("UNCHECKED_CAST")
                add(9, "default",
                    ContextualValueDefinition(
                        contextualResolver = { context: EnumDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                        }
                    ) as IsSerializableFlexBytesEncodable<IndexedEnum<*>, IsPropertyContext>,
                    getter = EnumDefinition<*>::default
                )
            }
        }
    ) {

        override fun invoke(map: Map<Int, *>): EnumDefinition<IndexedEnum<Any>> {
            val valueMap = map<Map<UInt32, String>>(6).map {
                Pair(it.key, IndexedEnum(it.key.toInt(), it.value))
            }.toMap()

            @Suppress("UNCHECKED_CAST")
            val values = valueMap.values.toTypedArray() as Array<IndexedEnum<Any>>

            @Suppress("UNCHECKED_CAST")
            return EnumDefinition(
                indexed = map(0),
                searchable = map(1),
                required = map(2),
                final = map(3),
                unique = map(4),
                enum = IndexedEnumDefinition(map(5), { values }),
                minValue = map(7),
                maxValue = map(8),
                default = map(9)
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
    for (it in enumValues) {
        result = 31 * result + it.index.hashCode()
    }
    return result
}

class EnumDefinitionContext() : IsPropertyContext {
    var name: String? = null
    var values: Map<UInt32, String>? = null

    private var _enumDefinition: Lazy<EnumDefinition<IndexedEnum<Any>>> = lazy {
        values?.let { values ->
            val valueMap = values.map {
                Pair(it.key, IndexedEnum(it.key.toInt(), it.value))
            }.toMap()

            @Suppress("UNCHECKED_CAST")
            val convertedValues = valueMap.values.toTypedArray() as Array<IndexedEnum<Any>>

            EnumDefinition(
                enum = IndexedEnumDefinition(
                    name ?: throw ContextNotFoundException() ,
                    { convertedValues }
                )
            )
        } ?: throw ContextNotFoundException()
    }

    val enumDefinition: EnumDefinition<IndexedEnum<Any>> get() = this._enumDefinition.value
}
