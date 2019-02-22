package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SingleTypedValueDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsValuePropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import kotlin.experimental.xor

/** Class to reverse key parts of type [T] by [reference] in key. */
data class Reversed<T: Any>(
    val reference: IsValuePropertyReference<T, *, *, *>
) : IsIndexablePropertyReference<T> {
    override val indexKeyPartType = IndexKeyPartType.Reversed

    override fun <DM : IsValuesDataModel<*>> getValue(values: Values<DM, *>) =
        this.reference.getValue(values)

    override fun calculateStorageByteLength(value: T) =
        this.reference.calculateStorageByteLength(value)

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) {
        this.reference.writeStorageBytes(value) {
            writer(MAX_BYTE xor it)
        }
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte): T {
        return this.reference.readStorageBytes(length) {
            MAX_BYTE xor reader()
        }
    }

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        this.reference == propertyReference


    override fun calculateReferenceStorageByteLength(): Int {
        val refLength = this.reference.calculateStorageByteLength()
        return refLength.calculateVarIntWithExtraInfoByteSize() + refLength
    }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        this.reference.calculateStorageByteLength().writeVarIntWithExtraInfo(
            this.indexKeyPartType.index.toByte(),
            writer
        )
        this.reference.writeStorageBytes(writer)
    }

    object Properties : ObjectPropertyDefinitions<Reversed<out Any>>() {
        val reference = add(1, "reference",
            ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                contextualResolver = { it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException() }
            ),
            getter = {
                @Suppress("UNCHECKED_CAST")
                it.reference as IsPropertyReference<Any, *, *>
            }
        )
    }

    internal object Model : SingleTypedValueDataModel<AnyPropertyReference, Reversed<out Any>, Properties, DefinitionsConversionContext>(
        properties = Properties,
        singlePropertyDefinition = Properties.reference
    ) {
        override fun invoke(values: ObjectValues<Reversed<out Any>, Properties>) = Reversed<Any>(
            reference = values(1)
        )
    }
}
