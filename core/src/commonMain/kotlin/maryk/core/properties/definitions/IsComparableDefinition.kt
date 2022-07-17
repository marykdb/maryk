package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.OutOfRangeException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Property Definition to define comparable properties of type [T] with context [CX].
 * This is used for simple single value properties and not for lists and maps.
 */
@Suppress("RemoveExplicitSuperQualifier")
interface IsComparableDefinition<T : Comparable<T>, in CX : IsPropertyContext> : IsSimpleValueDefinition<T, CX> {
    val unique: Boolean
    val minValue: T?
    val maxValue: T?

    // Overridden because the compiler has issues finding this method in the override
    override fun calculateTransportByteLengthWithKey(index: UInt, value: T, cacher: WriteCacheWriter): Int {
        return super<IsSimpleValueDefinition>.calculateTransportByteLengthWithKey(index, value, cacher)
    }

    // Overridden because the compiler has issues finding this method in the override
    override fun writeTransportBytesWithKey(
        index: UInt,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit
    ) {
        super<IsSimpleValueDefinition>.writeTransportBytesWithKey(index, value, cacheGetter, writer)
    }

    // Overridden because the compiler has issues finding this method in the override
    override fun calculateTransportByteLengthWithKey(
        index: UInt,
        value: T,
        cacher: WriteCacheWriter,
        context: CX?,
    ): Int = super<IsSimpleValueDefinition>.calculateTransportByteLengthWithKey(index, value, cacher, context)

    // Overridden because the compiler has issues finding this method in the override
    override fun writeTransportBytesWithKey(
        index: UInt,
        value: T,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        super<IsSimpleValueDefinition>.writeTransportBytesWithKey(index, value, cacheGetter, writer, context)
    }

    /**
     * Validate [newValue] against [previousValue] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationException thrown if property is invalid
     */
    override fun validateWithRef(
        previousValue: T?,
        newValue: T?,
        refGetter: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?
    ) {
        super.validateWithRef(previousValue, newValue, refGetter)
        when {
            newValue != null -> {
                when {
                    this.minValue?.let {
                        newValue < it
                    } ?: false
                    -> throw OutOfRangeException(
                        refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                    )
                    this.maxValue?.let {
                        newValue > it
                    } ?: false
                    -> throw OutOfRangeException(
                        refGetter(), newValue.toString(), this.minValue.toString(), this.maxValue.toString()
                    )
                }
            }
        }
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super.compatibleWith(definition, addIncompatibilityReason)

        (definition as? IsComparableDefinition<*, *>)?.let {
            if (definition.unique != this.unique) {
                addIncompatibilityReason?.invoke("Unique cannot be made non unique and other way around")
                compatible = false
            }

            @Suppress("UNCHECKED_CAST")
            if (this.maxValue != null && (definition.maxValue == null || (this.maxValue!!::class == definition.maxValue!!::class && this.maxValue!! < definition.maxValue as T))) {
                addIncompatibilityReason?.invoke("Maximum value cannot be lower than original")
                compatible = false
            }

            @Suppress("UNCHECKED_CAST")
            if (this.minValue != null && (definition.minValue == null || (this.minValue!!::class == definition.minValue!!::class && this.minValue!! > definition.minValue as T))) {
                addIncompatibilityReason?.invoke("Minimum value cannot be higher than original")
                compatible = false
            }
        }

        return compatible
    }
}
