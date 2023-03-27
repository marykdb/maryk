package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsRootModel
import maryk.core.properties.SingleTypedValueModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.references.IsValuePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues
import kotlin.experimental.xor

/** Class to reverse key parts of type [T] by [reference] in key. */
data class Reversed<T : Any>(
    val reference: IsValuePropertyReference<T, *, *, *>
) : IsIndexablePropertyReference<T> {
    override val indexKeyPartType = IndexKeyPartType.Reversed
    override val referenceStorageByteArray by lazy { Bytes(this.toReferenceStorageByteArray()) }

    override fun getValue(values: IsValuesGetter) =
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

    override fun isCompatibleWithModel(dataModel: IsRootModel): Boolean =
        reference.isCompatibleWithModel(dataModel)

    internal object Model :
        SingleTypedValueModel<AnyPropertyReference, Reversed<out Any>, Model, DefinitionsConversionContext>(
            singlePropertyDefinitionGetter = { Model.reference }
        ) {
        val reference by contextual(
            index = 1u,
            getter = Reversed<*>::reference,
            definition = ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                contextualResolver = {
                    it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<Reversed<out Any>, Model>) = Reversed<Any>(
            reference = values(1u)
        )
    }
}
