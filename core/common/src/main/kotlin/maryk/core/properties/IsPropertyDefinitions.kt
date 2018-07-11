package maryk.core.properties

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference

interface IsPropertyDefinitions {
    /** Get PropertyReference by [referenceName] */
    fun getPropertyReferenceByName(referenceName: String): IsPropertyReference<*, IsPropertyDefinition<*>>

    /** Get PropertyReference by bytes from [reader] with [length] */
    fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>>
}
