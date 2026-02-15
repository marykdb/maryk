package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** Definition for Boolean properties */
data class BooleanDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val default: Boolean? = null
) :
    IsSimpleValueDefinition<Boolean, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Boolean, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Boolean>,
    HasDefaultValueDefinition<Boolean> {
    override val propertyDefinitionType = PropertyDefinitionType.Boolean
    override val wireType = VAR_INT
    override val byteSize = 1

    override fun readStorageBytes(length: Int, reader: () -> Byte) = initBoolean(reader)

    override fun calculateStorageByteLength(value: Boolean) = this.byteSize

    override fun writeStorageBytes(value: Boolean, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Boolean) = this.byteSize

    override fun fromString(string: String) = when (string) {
        "true" -> true
        "false" -> false
        else -> throw ParseException(string)
    }

    override fun fromNativeType(value: Any) = value as? Boolean

    override fun writeJsonValue(value: Boolean, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        writer.writeBoolean(value)
    }

    object Model : SimpleObjectModel<BooleanDefinition, IsObjectDataModel<BooleanDefinition>>() {
        val required by boolean(1u, BooleanDefinition::required, default = true)
        val final by boolean(2u, BooleanDefinition::final, default = false)
        val default by boolean(3u, BooleanDefinition::default)

        override fun invoke(values: SimpleObjectValues<BooleanDefinition>) = BooleanDefinition(
            required = values(1u),
            final = values(2u),
            default = values(3u)
        )
    }
}


fun IsValuesDataModel.boolean(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    default: Boolean? = null,
    alternativeNames: Set<String>? = null,
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<Boolean, Boolean, IsPropertyContext, BooleanDefinition, Any>(
        index,
        name ?: propName,
        BooleanDefinition(required, final, default),
        alternativeNames,
        sensitive,
    )
}

fun <DO: Any> IsObjectDataModel<DO>.boolean(
    index: UInt,
    getter: (DO) -> Boolean?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    default: Boolean? = null,
    alternativeNames: Set<String>? = null,
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<Boolean, Boolean, IsPropertyContext, BooleanDefinition, DO>, DO, IsPropertyContext> =
    boolean(index, getter, name, required, final, sensitive, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.boolean(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    default: Boolean? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> Boolean?)? = null,
    fromSerializable: ((Boolean?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, Boolean) -> Unit)? = null,
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        BooleanDefinition(required, final, default),
        alternativeNames,
        sensitive,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
