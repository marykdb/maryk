package maryk.core.properties.definitions

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitionType.FixedBytes
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.values.SimpleObjectValues
import kotlin.random.Random

/** Definition for a byte array with fixed length */
data class FixedBytesDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Bytes? = null,
    override val maxValue: Bytes? = null,
    override val default: Bytes? = null,
    override val byteSize: Int
) :
    IsNumericDefinition<Bytes>,
    IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Bytes>,
    HasDefaultValueDefinition<Bytes> {
    override val propertyDefinitionType = FixedBytes
    override val wireType = LENGTH_DELIMITED

    override fun createRandom() = Bytes(Random.nextBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes(string)

    override fun fromNativeType(value: Any) =
        if (value is ByteArray && value.size == this.byteSize) {
            Bytes(value)
        } else {
            value as? Bytes
        }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?,
    ): Boolean {
        var compatible = super<IsNumericDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        (definition as? FixedBytesDefinition)?.let {
            if (definition.byteSize != this.byteSize) {
                addIncompatibilityReason?.invoke("Byte size for fixed bytes properties should be equal. $byteSize != ${definition.byteSize}")
                compatible = false
            }
        }

        return compatible
    }

    object Model : SimpleObjectModel<FixedBytesDefinition, IsObjectDataModel<FixedBytesDefinition>>() {
        val required by boolean(1u, FixedBytesDefinition::required, default = true)
        val final by boolean(2u, FixedBytesDefinition::final, default = false)
        val unique by boolean(3u, FixedBytesDefinition::unique, default = false)
        val minValue by flexBytes(4u, FixedBytesDefinition::minValue)
        val maxValue by flexBytes(5u, FixedBytesDefinition::maxValue)
        val default by flexBytes(6u, FixedBytesDefinition::default)
        val byteSize by number(
            7u,
            getter = FixedBytesDefinition::byteSize,
            type = UInt32,
            toSerializable = { value, _: IsPropertyContext? ->
                value?.toUInt()
            },
            fromSerializable = { it?.toInt() }
        )

        override fun invoke(values: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u),
            byteSize = values(7u)
        )
    }
}

fun IsValuesDataModel.fixedBytes(
    index: UInt,
    byteSize: Int,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    alternativeNames: Set<String>? = null,
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FixedBytesDefinition, Any>(
        index,
        name ?: propName,
        FixedBytesDefinition(required, final, unique, minValue, maxValue, default, byteSize),
        alternativeNames,
        sensitive,
    )
}

fun <TO: Any, DO: Any> IsObjectDataModel<DO>.fixedBytes(
    index: UInt,
    getter: (DO) -> TO?,
    byteSize: Int,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    alternativeNames: Set<String>? = null,
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<Bytes, TO, IsPropertyContext, FixedBytesDefinition, DO>, DO, IsPropertyContext> =
    fixedBytes(index, getter, byteSize, name, required, final, sensitive,  unique, minValue, maxValue, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.fixedBytes(
    index: UInt,
    getter: (DO) -> TO?,
    byteSize: Int,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> Bytes?)? = null,
    fromSerializable: ((Bytes?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, Bytes) -> Unit)? = null,
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        FixedBytesDefinition(required, final, unique, minValue, maxValue, default, byteSize),
        alternativeNames,
        sensitive,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
