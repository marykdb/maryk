package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.decodeStorageIndex

typealias IsAnyEmbeddedDefinition = IsEmbeddedDefinition<IsDataModel<AbstractPropertyDefinitions<Any>>, AbstractPropertyDefinitions<Any>>

/** Interface for property definitions containing embedded DataObjects of [DM] and definitions [P]. */
interface IsEmbeddedDefinition<out DM : IsDataModel<P>, P: AbstractPropertyDefinitions<*>> {
    val dataModel: DM

    /** Resolve a reference from [reader] found on a [parentReference] */
    fun resolveReference(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        val index = initIntByVar(reader)
        return this.dataModel.properties[index]?.getRef(parentReference)
            ?: throw DefNotFoundException("Embedded Definition with $index not found")
    }

    /** Resolve a reference from storage from [reader] found on a [parentReference] */
    fun resolveReferenceFromStorage(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null,
        context: IsPropertyContext?,
        isDoneReading: () -> Boolean
    ): AnyPropertyReference {
        return decodeStorageIndex(reader) { index, type ->
            val propertyReference = this.dataModel.properties[index]?.getRef(parentReference)
                ?: throw DefNotFoundException("Embedded Definition with $index not found")

            if (isDoneReading()) {
                propertyReference
            } else {
                when (propertyReference) {
                    is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedStorageRef(reader, context, type, isDoneReading)
                    else -> throw DefNotFoundException("More property references found on property that cannot have any: $propertyReference")
                }
            }
        }
    }

    /** Resolve a reference from [name] found on a [parentReference] */
    fun resolveReferenceByName(
        name: String,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        return this.dataModel.properties[name]?.getRef(parentReference)
            ?: throw DefNotFoundException("Embedded Definition with $name not found")
    }
}
