package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.DefinitionWithContextDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.values.Values
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.references.MultiTypePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext

/**
 * Defines a key part which refers to a multi type definition with [reference].
 * With this key part it is possible to query all objects which contain a property of a certain type
 */
data class TypeId<E: IndexedEnum<E>>(
    val reference: MultiTypePropertyReference<E, TypedValue<E, *>, MultiTypeDefinitionWrapper<E, TypedValue<E, *>, IsPropertyContext, *>, *>
) : FixedBytesProperty<Int> {
    override val keyPartType = KeyPartType.TypeId
    override val byteSize = 2

    constructor(multiTypeDefinition: MultiTypeDefinitionWrapper<E, TypedValue<E, *>, IsPropertyContext, *>) : this(reference = multiTypeDefinition.getRef())

    override fun <DO : Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsObjectDataModel<DO, P>, dataObject: DO): Int {
        @Suppress("UNCHECKED_CAST")
        val multiType = dataModel.properties.getPropertyGetter(
            reference.propertyDefinition.index
        )?.invoke(dataObject) as TypedValue<*, *>
        return multiType.type.index
    }

    override fun <DM : IsValuesDataModel<*>> getValue(dataModel: DM, values: Values<DM, *>): Int {
        val typedValue = values<TypedValue<*, *>?>(reference.propertyDefinition.index)
                ?: throw RequiredException(reference)
        return typedValue.type.index
    }

    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) {
        (value + Short.MIN_VALUE).toShort().writeBytes(writer)
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        initShort(reader).toInt() - Short.MIN_VALUE

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
        override fun invoke(map: SimpleObjectValues<TypeId<*>>) = TypeId<IndexedEnum<Any>>(
            reference = map(1)
        )
    }
}
