package maryk.core.properties.definitions

import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.SIGN_BYTE
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.SInt32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.values.SimpleObjectValues
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes
import maryk.lib.exceptions.ParseException
import kotlin.experimental.xor
import kotlin.random.Random

/** Definition for exact fixed-scale decimal properties. */
class DecimalDefinition(
    val scale: UInt,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    minValue: Decimal? = null,
    maxValue: Decimal? = null,
    default: Decimal? = null,
    override val reversedStorage: Boolean? = null,
    override val byteSize: Int = 8,
) :
    IsArithmeticDefinition<Decimal>,
    IsRandomizableDefinition<Decimal>,
    IsReversibleStorageDefinition,
    IsSerializableFixedBytesEncodable<Decimal, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Decimal>,
    HasDefaultValueDefinition<Decimal> {
    override val propertyDefinitionType = PropertyDefinitionType.Decimal
    override val wireType = LENGTH_DELIMITED
    override val minValue = minValue?.normalize()
    override val maxValue = maxValue?.normalize()
    override val default = default?.normalize()

    init {
        require(scale <= Decimal.MAX_SCALE) {
            "Decimal scale must be between 0 and ${Decimal.MAX_SCALE}"
        }
        require(byteSize in 1..MAX_STORAGE_BYTE_SIZE) {
            "Decimal storage byte size must be between 1 and $MAX_STORAGE_BYTE_SIZE"
        }
        require(this.minValue == null || this.maxValue == null || this.minValue <= this.maxValue) {
            "Decimal minimum cannot be greater than maximum"
        }
    }

    private fun Decimal.normalize(): Decimal = try {
        rescaleExact(this@DecimalDefinition.scale).also(::requireFits)
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("Decimal value cannot be represented at scale $scale", error)
    }

    private fun requireFits(value: Decimal): Decimal {
        require(value.fitsInSignedBytes(byteSize)) {
            "Decimal value does not fit in $byteSize storage bytes"
        }
        return value
    }

    internal fun maximumStorageValue(): Decimal = Decimal.maximumForSignedBytes(byteSize, scale)

    override fun createRandom(): Decimal = Decimal.fromSignedBytes(
        ByteArray(byteSize) { Random.nextInt().toByte() },
        scale,
    )

    override fun add(value1: Decimal, value2: Decimal): Decimal {
        return requireFits(value1.normalize() + value2.normalize())
    }

    override fun average(sum: Decimal, count: Long): Decimal {
        require(count > 0L) { "Average count must be positive" }
        val (quotient, remainder) = sum.normalize().divideAndRemainder(count)
        if (remainder == Decimal.fromUnscaled(0, scale)) return requireFits(quotient)
        val comparison = remainder.absoluteTwiceCompareTo(count)
        return requireFits(
            if (comparison > 0 || (comparison == 0 && quotient.isOdd())) quotient.roundedAwayFromZero() else quotient
        )
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte): Decimal {
        if (length != byteSize) {
            throw ParseException("Invalid storage byte length for Decimal: $length != $byteSize")
        }
        val bytes = ByteArray(byteSize) {
            if (reversedStorage == true) MAX_BYTE xor reader() else reader()
        }
        bytes[0] = bytes[0] xor SIGN_BYTE
        return Decimal.fromSignedBytes(bytes, scale)
    }

    override fun calculateStorageByteLength(value: Decimal) = byteSize

    override fun writeStorageBytes(value: Decimal, writer: (byte: Byte) -> Unit) {
        val normalized = try {
            value.rescaleExact(scale).also(::requireFits)
        } catch (error: IllegalArgumentException) {
            throw ParseException("Decimal value cannot be represented in $byteSize storage bytes", error)
        } catch (error: ArithmeticException) {
            throw ParseException("Decimal value cannot be represented at scale $scale", error)
        }
        val bytes = normalized.toSignedBytes(byteSize)
        bytes[0] = bytes[0] xor SIGN_BYTE
        bytes.forEach { writer(if (reversedStorage == true) MAX_BYTE xor it else it) }
    }

    override fun calculateTransportByteLength(value: Decimal): Int =
        value.rescaleForTransport().toString().calculateUTF8ByteLength()

    override fun writeTransportBytes(
        value: Decimal,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?,
    ) {
        value.rescaleForTransport().toString().writeUTF8Bytes(writer)
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: Decimal?,
    ): Decimal = fromString(initString(length, reader))

    private fun Decimal.rescaleForTransport(): Decimal = try {
        rescaleExact(scale).also(::requireFits)
    } catch (error: IllegalArgumentException) {
        throw ParseException("Decimal value cannot be represented in $byteSize storage bytes", error)
    } catch (error: ArithmeticException) {
        throw ParseException("Decimal value cannot be represented at scale $scale", error)
    }

    override fun fromString(string: String): Decimal = try {
        Decimal.parse(string).rescaleExact(scale).also(::requireFits)
    } catch (error: IllegalArgumentException) {
        throw ParseException(string, error)
    } catch (error: ArithmeticException) {
        throw ParseException(string, error)
    }

    override fun fromNativeType(value: Any): Decimal? =
        (value as? Decimal)?.let {
            try {
                it.rescaleExact(scale).also(::requireFits)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: ArithmeticException) {
                null
            }
        }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?,
    ): Boolean {
        var compatible = super<IsArithmeticDefinition>.compatibleWith(
            definition,
            checkedDataModelNames,
            addIncompatibilityReason,
        )
        if (definition is DecimalDefinition) {
            if (scale != definition.scale) {
                addIncompatibilityReason?.invoke("Decimal scale has to stay the same. $scale != ${definition.scale}")
                compatible = false
            }
            if (reversedStorage != definition.reversedStorage) {
                addIncompatibilityReason?.invoke("Reversed storage for decimal has to stay the same")
                compatible = false
            }
            if (byteSize != definition.byteSize) {
                addIncompatibilityReason?.invoke("Storage byte size for decimal has to stay the same")
                compatible = false
            }
        }
        return compatible
    }

    override fun equals(other: Any?): Boolean =
        other is DecimalDefinition &&
            scale == other.scale &&
            required == other.required &&
            final == other.final &&
            unique == other.unique &&
            minValue == other.minValue &&
            maxValue == other.maxValue &&
            default == other.default &&
            reversedStorage == other.reversedStorage &&
            byteSize == other.byteSize

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + unique.hashCode()
        result = 31 * result + (minValue?.hashCode() ?: 0)
        result = 31 * result + (maxValue?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        result = 31 * result + (reversedStorage?.hashCode() ?: 0)
        result = 31 * result + byteSize
        return result
    }

    companion object {
        const val MAX_STORAGE_BYTE_SIZE = 128
    }

    object Model : SimpleObjectModel<DecimalDefinition, IsObjectDataModel<DecimalDefinition>>() {
        val required by boolean(1u, DecimalDefinition::required, default = true)
        val final by boolean(2u, DecimalDefinition::final, default = false)
        val unique by boolean(3u, DecimalDefinition::unique, default = false)
        val scale by number(4u, DecimalDefinition::scale, type = UInt32)
        val minValue by string(
            5u,
            getter = DecimalDefinition::minValue,
            toSerializable = { value, _: IsPropertyContext? -> value?.toString() },
            fromSerializable = { it?.let(Decimal::parse) },
        )
        val maxValue by string(
            6u,
            getter = DecimalDefinition::maxValue,
            toSerializable = { value, _: IsPropertyContext? -> value?.toString() },
            fromSerializable = { it?.let(Decimal::parse) },
        )
        val default by string(
            7u,
            getter = DecimalDefinition::default,
            toSerializable = { value, _: IsPropertyContext? -> value?.toString() },
            fromSerializable = { it?.let(Decimal::parse) },
        )
        val reversedStorage by boolean(8u, DecimalDefinition::reversedStorage, required = false)
        val byteSize by number(9u, DecimalDefinition::byteSize, type = SInt32, default = 8)

        override fun invoke(values: SimpleObjectValues<DecimalDefinition>) = DecimalDefinition(
            scale = values(4u),
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(5u),
            maxValue = values(6u),
            default = values(7u),
            reversedStorage = values(8u),
            byteSize = values(9u),
        )
    }
}

fun IsValuesDataModel.decimal(
    index: UInt,
    scale: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Decimal? = null,
    maxValue: Decimal? = null,
    default: Decimal? = null,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null,
    byteSize: Int = 8,
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<Decimal, Decimal, IsPropertyContext, DecimalDefinition, Any>(
        index,
        name ?: propName,
        DecimalDefinition(scale, required, final, unique, minValue, maxValue, default, reversedStorage, byteSize),
        alternativeNames,
        sensitive,
    )
}

fun <TO : Any, DO : Any> IsObjectDataModel<DO>.decimal(
    index: UInt,
    getter: (DO) -> TO?,
    scale: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Decimal? = null,
    maxValue: Decimal? = null,
    default: Decimal? = null,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null,
    byteSize: Int = 8,
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<Decimal, TO, IsPropertyContext, DecimalDefinition, DO>, DO, IsPropertyContext> =
    decimal(
        index,
        getter,
        scale,
        name,
        required,
        final,
        sensitive,
        unique,
        minValue,
        maxValue,
        default,
        reversedStorage,
        alternativeNames,
        toSerializable = null,
        byteSize = byteSize,
    )

fun <TO : Any, DO : Any, CX : IsPropertyContext> IsObjectDataModel<DO>.decimal(
    index: UInt,
    getter: (DO) -> TO?,
    scale: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    sensitive: Boolean = false,
    unique: Boolean = false,
    minValue: Decimal? = null,
    maxValue: Decimal? = null,
    default: Decimal? = null,
    reversedStorage: Boolean? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: ((TO?, CX?) -> Decimal?)? = null,
    fromSerializable: ((Decimal?) -> TO?)? = null,
    shouldSerialize: ((Any) -> Boolean)? = null,
    capturer: ((CX, Decimal) -> Unit)? = null,
    byteSize: Int = 8,
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        DecimalDefinition(scale, required, final, unique, minValue, maxValue, default, reversedStorage, byteSize),
        alternativeNames,
        sensitive,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize,
    )
}
