package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.DefinitionDataModel
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
    Ref(1),
    TypeId(2),
    Reversed(3)
}

internal val mapOfKeyPartDefinitions = mapOf<KeyPartType, IsSubDefinition<*, DataModelContext>>(
    KeyPartType.UUID to SubModelDefinition(dataModel = { UUIDKey.Model }),
    KeyPartType.Ref to ContextualPropertyReferenceDefinition(
        contextualResolver = {
            it?.propertyDefinitions ?: throw ContextNotFoundException()
        }
    ),
    KeyPartType.TypeId to SubModelDefinition(dataModel = {
        @Suppress("UNCHECKED_CAST")
        TypeId.Model as DefinitionDataModel<TypeId<IndexedEnum<Any>>>
    }),
    KeyPartType.Reversed to SubModelDefinition(dataModel = { Reversed.Model })
)
