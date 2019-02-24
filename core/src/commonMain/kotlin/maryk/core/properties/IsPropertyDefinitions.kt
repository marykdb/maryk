package maryk.core.properties

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference

@PropertyReferenceMarker
interface IsPropertyDefinitions {
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
}
