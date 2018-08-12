package maryk.core.properties.definitions.key

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.DefinitionDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.query.DefinitionsContext
import maryk.json.TokenType
import maryk.json.ValueType

/** Indexed type of property definitions */
sealed class KeyPartType(
    override val name: String,
    override val index: Int
): IndexedEnum<KeyPartType>, TokenType {
    override fun compareTo(other: KeyPartType) =
        this.index.compareTo(other.index)

    object UUID: KeyPartType("UUID", 1), ValueType.IsNullValueType
    object Reference: KeyPartType("Ref", 2), ValueType<String>
    object TypeId: KeyPartType("TypeId", 3), ValueType<String>
    object Reversed: KeyPartType("Reversed", 4), ValueType<String>

    companion object: IndexedEnumDefinition<KeyPartType>(
        "KeyPartType", { keyPartValues }
    )
}

val keyPartValues = arrayOf<KeyPartType>(KeyPartType.UUID, KeyPartType.Reference, KeyPartType.TypeId, KeyPartType.Reversed)

internal val mapOfKeyPartDefinitions = mapOf<KeyPartType, IsSubDefinition<*, DefinitionsContext>>(
    KeyPartType.UUID to EmbeddedObjectDefinition(dataModel = { UUIDKey.Model }),
    KeyPartType.Reference to ContextualPropertyReferenceDefinition(
        contextualResolver = {
            it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
        }
    ),
    KeyPartType.TypeId to EmbeddedObjectDefinition(dataModel = {
        @Suppress("UNCHECKED_CAST")
        TypeId.Model as DefinitionDataModel<TypeId<IndexedEnum<Any>>>
    }),
    KeyPartType.Reversed to EmbeddedObjectDefinition(dataModel = { Reversed.Model })
)
