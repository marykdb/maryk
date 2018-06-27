package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.objects.SimpleValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.DataModelContext
import maryk.lib.exceptions.ParseException

/** Definition for Enum properties */
class EnumDefinition<E : IndexedEnum<E>>(
    override val indexed: Boolean = false,
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
    HasDefaultValueDefinition<E>
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
        contextTransformer = { EnumDefinitionContext(it) },
        properties = object : PropertyDefinitions<EnumDefinition<*>>() {
            init {
                IsPropertyDefinition.addIndexed(this, EnumDefinition<*>::indexed)
                IsPropertyDefinition.addRequired(this, EnumDefinition<*>::required)
                IsPropertyDefinition.addFinal(this, EnumDefinition<*>::final)
                IsComparableDefinition.addUnique(this, EnumDefinition<*>::unique)
                @Suppress("UNCHECKED_CAST")
                add(4, "enum",
                    ContextValueTransformDefinition(
                        definition = ContextTransformerDefinition(
                            definition = EmbeddedObjectDefinition(
                                dataModel = { IndexedEnumDefinition.Model }
                            ),
                            contextTransformer = {
                                it?.dataModelContext
                            }
                        ),
                        valueTransformer = { context, value ->
                            if (value.optionalValues == null) {
                                context?.let {
                                    it.dataModelContext?.let {
                                        it.enums[value.name] as IndexedEnumDefinition<IndexedEnum<Any>>?
                                            ?: throw ParseException("Enum ${value.name} is not Defined")
                                    }
                                } ?: throw ContextNotFoundException()
                            } else {
                                value
                            }
                        }
                    ),
                    getter = EnumDefinition<*>::enum as (EnumDefinition<*>) -> IndexedEnumDefinition<IndexedEnum<Any>>,
                    capturer = { context: EnumDefinitionContext, value: IndexedEnumDefinition<IndexedEnum<Any>> ->
                        context.enumDefinition = EnumDefinition(enum = value)
                    }
                )
                @Suppress("UNCHECKED_CAST")
                add(5, "minValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: EnumDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                        }
                    ) as IsSerializableFlexBytesEncodable<IndexedEnum<*>, IsPropertyContext>,
                    getter = EnumDefinition<*>::minValue
                )
                @Suppress("UNCHECKED_CAST")
                add(6, "maxValue",
                    ContextualValueDefinition(
                        contextualResolver = { context: EnumDefinitionContext? ->
                            @Suppress("UNCHECKED_CAST")
                            context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                        }
                    ) as IsSerializableFlexBytesEncodable<IndexedEnum<*>, IsPropertyContext>,
                    getter = EnumDefinition<*>::maxValue
                )
                @Suppress("UNCHECKED_CAST")
                add(7, "default",
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
        override fun invoke(map: SimpleValueMap<EnumDefinition<*>>) = EnumDefinition<IndexedEnum<Any>>(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            enum = map(4),
            minValue = map(5),
            maxValue = map(6),
            default = map(7)
        )
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

class EnumDefinitionContext(
    val dataModelContext: DataModelContext?
) : IsPropertyContext {
    var enumDefinition: EnumDefinition<IndexedEnum<Any>>? = null
}
