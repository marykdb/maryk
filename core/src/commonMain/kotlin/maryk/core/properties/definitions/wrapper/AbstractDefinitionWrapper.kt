package maryk.core.properties.definitions.wrapper

import maryk.core.properties.references.IsPropertyReference

private val regEx = Regex("[a-z]+[a-zA-Z0-9]*")

abstract class AbstractDefinitionWrapper(
    index: UInt,
    name: String
): CacheableReferenceCreator {
    init {
        require(index > 0u) { "Index of property $name should be larger than 0 for ProtoBuf support" }
        require(index !in 19000u..19999u) { "Index of property $name cannot be within 19000 to 19999 for ProtoBuf support" }
        require(index <= 536870911u) { "Index of property $name cannot be larger than 536,870,911 for ProtoBuf support" }
        require(name.matches(regEx)) { "Property name has to start with a lower case letter and can only contain letters and numbers" }
    }

    /** Cache for all references, so they are not created over and over */
    override val refCache =
        mutableMapOf<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>()
}
