package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.lib.exceptions.ParseException

/** Definition for Enum properties */
class EnumDefinition<E : IndexedEnumComparable<E>>(
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
    HasDefaultValueDefinition<E> {
    override val propertyDefinitionType = PropertyDefinitionType.Enum
    override val wireType = VAR_INT
    override val byteSize = 2

    init {
        // Check enum
        enum.check()
    }

    private fun getEnumByIndex(index: UInt) =
        enum.resolve(index) ?: throw ParseException("Enum index does not exist $index")

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        getEnumByIndex(initUInt(reader, 2))

    override fun calculateStorageByteLength(value: E) = this.byteSize

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        value.index.writeBytes(writer, 2)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) =
        getEnumByIndex(initUIntByVar(reader))

    override fun calculateTransportByteLength(value: E) =
        value.index.calculateVarByteLength()

    override fun writeTransportBytes(
        value: E,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) =
        value.index.writeVarBytes(writer)

    override fun asString(value: E) =
        if (value is IsCoreEnum) {
            value.name
        } else {
            "${value.name}(${value.index})"
        }

    override fun fromString(string: String): E =
        enum.resolve(string) ?: throw ParseException(string)

    override fun fromNativeType(value: Any): E? = null

    /** Override equals to handle enum cases comparison */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnumDefinition<*>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (unique != other.unique) return false
        if (minValue != other.minValue && minValue?.index != other.minValue?.index) return false
        if (maxValue != other.maxValue && maxValue?.index != other.maxValue?.index) return false
        if (default != other.default && default?.index != other.default?.index) return false
        if (enum.name != other.enum.name) return false
        if (!areEnumsEqual(enum.cases(), other.enum.cases())) return false
        if (wireType != other.wireType) return false
        if (byteSize != other.byteSize) return false

        return true
    }

    /** Override hashCode to handle enum cases comparison */
    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + unique.hashCode()
        result = 31 * result + (minValue?.index?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.index?.hashCode() ?: 0)
        result = 31 * result + (default?.index?.hashCode() ?: 0)
        result = 31 * result + enum.name.hashCode()
        result = 31 * result + enumsHashCode(enum.cases())
        result = 31 * result + wireType.hashCode()
        result = 31 * result + byteSize
        return result
    }

    object Model :
        ContextualDataModel<EnumDefinition<*>, ObjectPropertyDefinitions<EnumDefinition<*>>, ContainsDefinitionsContext, EnumDefinitionContext>(
            contextTransformer = { EnumDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<EnumDefinition<*>>() {
                init {
                    IsPropertyDefinition.addRequired(this, EnumDefinition<*>::required)
                    IsPropertyDefinition.addFinal(this, EnumDefinition<*>::final)
                    IsComparableDefinition.addUnique(this, EnumDefinition<*>::unique)
                    @Suppress("UNCHECKED_CAST")
                    add(4u, "enum",
                        ContextValueTransformDefinition(
                            definition = ContextTransformerDefinition(
                                definition = EmbeddedObjectDefinition(
                                    dataModel = { IndexedEnumDefinition.Model }
                                ),
                                contextTransformer = {
                                    it?.definitionsContext
                                }
                            ),
                            valueTransformer = { context: EnumDefinitionContext?, value ->
                                if (value.optionalCases == null) {
                                    context?.let { c ->
                                        c.definitionsContext?.let {
                                            it.enums[value.name] as IndexedEnumDefinition<IndexedEnum>?
                                                ?: throw ParseException("Enum ${value.name} is not Defined")
                                        }
                                    } ?: throw ContextNotFoundException()
                                } else {
                                    value
                                }
                            }
                        ),
                        getter = EnumDefinition<*>::enum as (EnumDefinition<*>) -> IndexedEnumDefinition<IndexedEnum>,
                        capturer = { context, value ->
                            context.enumDefinition =
                                EnumDefinition(enum = value as IndexedEnumDefinition<IndexedEnumComparable<Any>>)
                        }
                    )
                    add(5u, "minValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: EnumDefinitionContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                            }
                        ),
                        getter = EnumDefinition<*>::minValue
                    )
                    add(6u, "maxValue",
                        ContextualValueDefinition(
                            contextualResolver = { context: EnumDefinitionContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                            }
                        ),
                        getter = EnumDefinition<*>::maxValue
                    )
                    add(7u, "default",
                        ContextualValueDefinition(
                            contextualResolver = { context: EnumDefinitionContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                            }
                        ),
                        getter = EnumDefinition<*>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<EnumDefinition<*>>) = EnumDefinition<IndexedEnumComparable<Any>>(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            enum = values(4u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u)
        )
    }
}

private fun areEnumsEqual(enumValues: Array<out IndexedEnum>, otherValues: Array<out IndexedEnum>) = when {
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

private fun enumsHashCode(enumValues: Array<out IndexedEnum>): Int {
    var result = 1
    for (it in enumValues) {
        result = 31 * result + it.index.hashCode()
    }
    return result
}

class EnumDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var enumDefinition: EnumDefinition<out IndexedEnumComparable<Any>>? = null
}
