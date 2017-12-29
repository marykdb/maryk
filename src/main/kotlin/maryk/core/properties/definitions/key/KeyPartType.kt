package maryk.core.properties.definitions.key

import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.types.IndexedEnum
import maryk.core.query.DataModelContext

/** Indexed type of property definitions */
enum class KeyPartType(
        override val index: Int
): IndexedEnum<KeyPartType> {
    UUID(0),
    Reference(1),
    TypeId(2),
    Reversed(3)
}

internal val mapOfKeyPartDefinitions = mapOf<Int, IsSubDefinition<*, DataModelContext>>(
        KeyPartType.UUID.index to SubModelDefinition(dataModel = { UUIDKey.Model }),
        KeyPartType.Reference.index to ContextualPropertyReferenceDefinition(
                contextualResolver = {
                    it!!.propertyDefinitions!!
                }
        ),
        KeyPartType.TypeId.index to SubModelDefinition(dataModel = { TypeId.Model }),
        KeyPartType.Reversed.index to SubModelDefinition(dataModel = { Reversed.Model })
)