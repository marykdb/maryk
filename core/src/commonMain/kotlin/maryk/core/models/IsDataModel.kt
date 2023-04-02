package maryk.core.models

import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyReferenceMarker
import maryk.core.properties.definitions.IsEmbeddedDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues

@PropertyReferenceMarker
interface IsDataModel {
    val Serializer : IsDataModelSerializer<*, *, *>

    /** Get the definition with a property [name] */
    operator fun get(name: String): IsDefinitionWrapper<*, *, *, *>?

    /** Get the definition with a property [index] */
    operator fun get(index: UInt): IsDefinitionWrapper<*, *, *, *>?

    /** Get PropertyReference by [referenceName] */
    fun getPropertyReferenceByName(
        referenceName: String,
        context: IsPropertyContext? = null
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *>

    /** Get PropertyReference by bytes from [reader] with [length] */
    fun getPropertyReferenceByBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext? = null
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *>

    /** Get PropertyReference by storage bytes from [reader] with [length] */
    fun getPropertyReferenceByStorageBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext? = null
    ): IsPropertyReference<*, IsPropertyDefinition<*>, *>

    /**
     * Checks if the DataModel is compatible with [propertyReference]
     * This is useful to test if reference is compatible after migration with already stored model.
     * This result can be used to know if an index has to be indexed with existing values.
     */
    fun compatibleWithReference(propertyReference: AnyPropertyReference): Boolean {
        val unwrappedReferences = propertyReference.unwrap()

        var model: IsDataModel = this

        for (reference in unwrappedReferences) {
            if (reference is IsPropertyReferenceForValues<*, *, *, *>) {
                when(val storedPropertyDefinition = model[reference.index]) {
                    null -> return false
                    else -> {
                        val propertyDefinition = reference.propertyDefinition

                        if (propertyDefinition is IsEmbeddedDefinition<*>) {
                            if (storedPropertyDefinition !is IsEmbeddedDefinition<*>) {
                                return false // Types are not matching
                            } else {
                                model = storedPropertyDefinition.dataModel
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}

/**
 * Get property reference fetcher of this DataModel with [referenceGetter]
 * Optionally pass an already resolved [parent]
 * For Strongly typed reference notation
 */
operator fun <T : Any, DM: IsDataModel, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> DM.invoke(
    parent: AnyOutPropertyReference? = null,
    referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
) = referenceGetter(this)(parent)
