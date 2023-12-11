package maryk.core.properties.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextValueTransformDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.ObjectValues
import maryk.lib.exceptions.ParseException

/** Definition for Enum properties */
data class EnumDefinition<E : IndexedEnumComparable<E>>(
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
    override val wireType = enum.wireType
    override val byteSize = enum.byteSize

    init {
        // Check enum
        enum.check()
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        enum.readStorageBytes(length, reader)

    override fun calculateStorageByteLength(value: E) = enum.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: E, writer: (byte: Byte) -> Unit) {
        enum.writeStorageBytes(value, writer)
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: E?
    ) =
        enum.readTransportBytes(length, reader, context, earlierValue)

    override fun calculateTransportByteLength(value: E) =
        enum.calculateTransportByteLength(value)

    override fun writeTransportBytes(
        value: E,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) =
        enum.writeTransportBytes(value, cacheGetter, writer, context)

    override fun asString(value: E) =
        enum.asString(value)

    override fun fromString(string: String): E =
        enum.resolve(string) ?: throw ParseException(string)

    @Suppress("UNCHECKED_CAST")
    override fun fromNativeType(value: Any): E? = value as? E

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

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsSerializableFixedBytesEncodable>.compatibleWith(definition, addIncompatibilityReason)

        if (definition is EnumDefinition) {
            compatible = enum.compatibleWith(definition.enum, addIncompatibilityReason) && compatible
        }

        return compatible
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        if (!dependencySet.contains(enum)) {
            enum
        }
    }

    object Model : ContextualDataModel<EnumDefinition<*>, Model, ContainsDefinitionsContext, EnumDefinitionContext>(
        contextTransformer = { EnumDefinitionContext(it) },
    ) {
        val required by boolean(1u, EnumDefinition<*>::required, default = true)
        val final by boolean(2u, EnumDefinition<*>::final, default = false)
        val unique by boolean(3u, EnumDefinition<*>::unique, default = false)
        val enum by contextual(
            index = 4u,
            getter = EnumDefinition<*>::enum,
            definition = ContextValueTransformDefinition(
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
                                it.enums[value.name] ?: throw ParseException("Enum ${value.name} is not Defined")
                            }
                        } ?: throw ContextNotFoundException()
                    } else {
                        value
                    }
                }
            ),
            capturer = { context, value ->
                @Suppress("UNCHECKED_CAST")
                context.enumDefinition =
                    EnumDefinition(enum = value as IndexedEnumDefinition<IndexedEnumComparable<Any>>)
            }
        )
        val minValue by contextual(
            index = 5u,
            getter = EnumDefinition<*>::minValue,
            definition = ContextualValueDefinition(
                contextualResolver = { context: EnumDefinitionContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                }
            )
        )
        val maxValue by contextual(
            index = 6u,
            getter = EnumDefinition<*>::maxValue,
            definition = ContextualValueDefinition(
                contextualResolver = { context: EnumDefinitionContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                }
            )
        )
        val default by contextual(
            index = 7u,
            getter = EnumDefinition<*>::default,
            definition = ContextualValueDefinition(
                contextualResolver = { context: EnumDefinitionContext? ->
                    @Suppress("UNCHECKED_CAST")
                    context?.enumDefinition as IsValueDefinition<Any, IsPropertyContext>
                }
            )
        )

        override fun invoke(values: ObjectValues<EnumDefinition<*>, Model>): EnumDefinition<*> = EnumDefinition<IndexedEnumComparable<Any>>(
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

private fun areEnumsEqual(enumValues: List<IndexedEnum>, otherValues: List<IndexedEnum>) = when {
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

private fun enumsHashCode(enumValues: List<IndexedEnum>): Int {
    var result = 1
    for (it in enumValues) {
        result = 31 * result + it.index.hashCode()
    }
    return result
}

class EnumDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var enumDefinition: EnumDefinition<IndexedEnumComparable<Any>>? = null
}

fun <E : IndexedEnumComparable<E>> IsValuesDataModel.enum(
    index: UInt,
    enum: IndexedEnumDefinition<E>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: E? = null,
    maxValue: E? = null,
    default: E? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<E, E, IsPropertyContext, EnumDefinition<E>, Any>(
        index,
        name ?: propName,
        EnumDefinition(required, final, unique, minValue, maxValue, default, enum),
        alternativeNames
    )
}

fun <E : IndexedEnumComparable<E>, TO: Any, DO: Any> IsObjectDataModel<DO>.enum(
    index: UInt,
    getter: (DO) -> TO?,
    enum: IndexedEnumDefinition<E>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: E? = null,
    maxValue: E? = null,
    default: E? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<E, TO, IsPropertyContext, EnumDefinition<E>, DO>, DO, IsPropertyContext> =
    enum(index, getter, enum, name, required, final,  unique, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <E : IndexedEnumComparable<E>, TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.enum(
    index: UInt,
    getter: (DO) -> TO?,
    enum: IndexedEnumDefinition<E>,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: E? = null,
    maxValue: E? = null,
    default: E? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> E?)? = null,
    fromSerializable: (Unit.(E?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, E) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        EnumDefinition(required, final, unique, minValue, maxValue, default, enum),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
