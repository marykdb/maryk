package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.MAX_BYTE
import maryk.core.models.DefinitionWithContextDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.values.Values
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.query.DefinitionsConversionContext
import kotlin.experimental.xor

/** Class to reverse key parts of type [T] by [reference] in key. */
data class Reversed<T: Any>(
    val reference: ValueWithFixedBytesPropertyReference<T, *, FixedBytesPropertyDefinitionWrapper<T, *, *, *, *>, *>
) : FixedBytesProperty<T> {
    override val keyPartType = KeyPartType.Reversed
    override val byteSize = this.reference.propertyDefinition.byteSize
    override fun <DO : Any, P: ObjectPropertyDefinitions<DO>> getValue(dataModel: IsObjectDataModel<DO, P>, dataObject: DO) =
        this.reference.propertyDefinition.getValue(dataModel, dataObject)

    override fun <DM : IsValuesDataModel<*>> getValue(dataModel: DM, values: Values<DM, *>) =
        this.reference.propertyDefinition.getValue(dataModel, values)

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

    internal object Model : DefinitionWithContextDataModel<Reversed<out Any>, DefinitionsConversionContext>(
        properties = object : ObjectPropertyDefinitions<Reversed<out Any>>() {
            init {
                add(1, "multiTypeDefinition",
                    ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                        contextualResolver = { it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException() }
                    ),
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        it.reference as IsPropertyReference<Any, *, *>
                    }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Reversed<out Any>>) = Reversed<Any>(
            reference = values(1)
        )
    }
}
