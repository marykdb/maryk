package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.objects.DefinitionDataModel
import maryk.core.objects.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.ValuePropertyReference
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext

/**
 * Defines a key part which refers to a multi type definition with [multiTypeReference].
 * With this key part it is possible to query all objects which contain a property of a certain type
 */
data class TypeId<E: IndexedEnum<E>>(
    val multiTypeReference: ValuePropertyReference<TypedValue<E, *>, IsPropertyDefinitionWrapper<TypedValue<E, *>, IsPropertyContext, *>, *>
) : FixedBytesProperty<Int>() {
    override val keyPartType = KeyPartType.TypeId
    override val byteSize = 2

    constructor(multiTypeDefinition: PropertyDefinitionWrapper<TypedValue<E, *>, IsPropertyContext, *, *>) : this(multiTypeReference = multiTypeDefinition.getRef())

    override fun <T : Any> getValue(dataModel: IsDataModel<T>, dataObject: T): Int {
        val multiType = dataModel.properties.getPropertyGetter(
            multiTypeReference.propertyDefinition.index
        )?.invoke(dataObject) as TypedValue<*, *>
        return multiType.type.index
    }

    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) {
        (value + Short.MIN_VALUE).toShort().writeBytes(writer)
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        initShort(reader).toInt() - Short.MIN_VALUE

    internal object Model : DefinitionDataModel<TypeId<*>>(
        properties = object : PropertyDefinitions<TypeId<*>>() {
            init {
                add(0, "multiTypeDefinition", ContextualPropertyReferenceDefinition<DataModelContext>(
                    contextualResolver = {
                        it?.propertyDefinitions ?: throw ContextNotFoundException()
                    }
                )) {
                    it.multiTypeReference
                }
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = TypeId<IndexedEnum<Any>>(
            multiTypeReference = map(0)
        )
    }
}
