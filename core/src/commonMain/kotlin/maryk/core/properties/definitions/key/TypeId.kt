package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.DefinitionWithContextDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsFixedBytesPropertyReference
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.SimpleObjectValues
import maryk.core.values.Values

/**
 * Defines a key part which refers to a multi type definition with [reference].
 * With this key part it is possible to query all objects which contain a property of a certain type
 */
data class TypeId<E: IndexedEnum<E>>(
    val reference: MultiTypePropertyReference<E, TypedValue<E, *>, MultiTypeDefinitionWrapper<E, TypedValue<E, *>, IsPropertyContext, *>, *>
) : IsFixedBytesEncodable<UInt>, IsFixedBytesPropertyReference<UInt> {
    override val propertyDefinition = this
    override val keyPartType = KeyPartType.TypeId
    override val byteSize = 2

    override fun <DM : IsValuesDataModel<*>> getValue(dataModel: DM, values: Values<DM, *>): UInt {
        val typedValue = values<TypedValue<*, *>?>(reference.propertyDefinition.index)
                ?: throw RequiredException(reference)
        return typedValue.type.index
    }

    override fun writeStorageBytes(value: UInt, writer: (byte: Byte) -> Unit) {
        value.writeBytes(writer, 2)
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        initUInt(reader, 2)

    override fun isForPropertyReference(propertyReference: AnyPropertyReference) =
        this.reference == propertyReference

    internal object Model : DefinitionWithContextDataModel<TypeId<*>, DefinitionsConversionContext>(
        properties = object : ObjectPropertyDefinitions<TypeId<*>>() {
            init {
                add(1, "multiTypeDefinition",
                    ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                        contextualResolver = {
                            it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = TypeId<*>::reference
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<TypeId<*>>) = TypeId<IndexedEnum<Any>>(
            reference = values(1)
        )
    }
}
