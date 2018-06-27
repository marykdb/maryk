package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.DefinitionDataModel
import maryk.core.models.IsDataModelWithPropertyDefinition
import maryk.core.objects.SimpleValueMap
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.query.DataModelContext
import kotlin.experimental.xor

/** Class to reverse key parts of type [T] by [reference] in key. */
data class Reversed<T: Any>(
    val reference: ValueWithFixedBytesPropertyReference<T, FixedBytesPropertyDefinitionWrapper<T, *, *, *, *>, *>
) : FixedBytesProperty<T>() {
    override val keyPartType = KeyPartType.Reversed
    override val byteSize = this.reference.propertyDefinition.byteSize
    override fun <DO : Any, P: PropertyDefinitions<DO>> getValue(dataModel: IsDataModelWithPropertyDefinition<DO, P>, dataObject: DO) =
        this.reference.propertyDefinition.getValue(dataModel, dataObject)

    /** Convenience constructor to pass [definition] */
    constructor(definition: FixedBytesPropertyDefinitionWrapper<T, *, *, *, *>) : this(definition.getRef())

    override fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit) {
        this.reference.propertyDefinition.writeStorageBytes(value) {
            writer(MAX_BYTE xor it)
        }
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte): T {
        return this.reference.propertyDefinition.readStorageBytes(byteSize) {
            MAX_BYTE xor reader()
        }
    }

    internal object Model : DefinitionDataModel<Reversed<out Any>>(
        properties = object : PropertyDefinitions<Reversed<out Any>>() {
            init {
                add(0, "multiTypeDefinition",
                    ContextualPropertyReferenceDefinition<DataModelContext>(
                        contextualResolver = { it?.propertyDefinitions ?: throw ContextNotFoundException() }
                    ),
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        it.reference as IsPropertyReference<Any, *>
                    }
                )
            }
        }
    ) {
        override fun invoke(map: SimpleValueMap<Reversed<out Any>>) = Reversed<Any>(
            reference = map(0)
        )
    }
}
