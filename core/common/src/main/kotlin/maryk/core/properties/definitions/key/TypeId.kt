package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.references.ValuePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext

/**
 * Defines a key part which refers to a multi type definition with [reference].
 * With this key part it is possible to query all objects which contain a property of a certain type
 */
data class TypeId<E: IndexedEnum<E>>(
    val reference: ValuePropertyReference<TypedValue<E, *>, TypedValue<E, *>, IsPropertyDefinitionWrapper<TypedValue<E, *>, TypedValue<E, *>, IsPropertyContext, *>, *>
) : FixedBytesProperty<Int>() {
    override val keyPartType = KeyPartType.TypeId
    override val byteSize = 2

    constructor(multiTypeDefinition: PropertyDefinitionWrapper<TypedValue<E, *>, TypedValue<E, *>, IsPropertyContext, *, *>) : this(reference = multiTypeDefinition.getRef())

    override fun <DO : Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsObjectDataModel<DO, P>, dataObject: DO): Int {
        @Suppress("UNCHECKED_CAST")
        val multiType = dataModel.properties.getPropertyGetter(
            reference.propertyDefinition.index
        )?.invoke(dataObject) as TypedValue<*, *>
        return multiType.type.index
    }

    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) {
        (value + Short.MIN_VALUE).toShort().writeBytes(writer)
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        initShort(reader).toInt() - Short.MIN_VALUE

    internal object Model : DefinitionDataModel<TypeId<*>>(
        properties = object : ObjectPropertyDefinitions<TypeId<*>>() {
            init {
                add(0, "multiTypeDefinition",
                    ContextualPropertyReferenceDefinition<DataModelContext>(
                        contextualResolver = {
                            it?.propertyDefinitions as? ObjectPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = TypeId<*>::reference
                )
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<TypeId<*>>) = TypeId<IndexedEnum<Any>>(
            reference = map(0)
        )
    }
}
