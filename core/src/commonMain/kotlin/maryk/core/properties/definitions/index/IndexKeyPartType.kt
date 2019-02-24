package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.DefinitionDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.query.DefinitionsConversionContext
import maryk.json.TokenType
import maryk.json.ValueType

/** Indexed type of property definitions */
sealed class IndexKeyPartType(
    override val name: String,
    override val index: UInt
) : IndexedEnum<IndexKeyPartType>, TokenType {
    override fun compareTo(other: IndexKeyPartType) =
        this.index.compareTo(other.index)

    object UUID : IndexKeyPartType("UUID", 1u), ValueType.IsNullValueType
    object Reference : IndexKeyPartType("Ref", 2u), ValueType<String>
    object TypeId : IndexKeyPartType("TypeId", 3u), ValueType<String>
    object Reversed : IndexKeyPartType("Reversed", 4u), ValueType<String>
    object Multiple : IndexKeyPartType("Multiple", 5u)

    companion object : IndexedEnumDefinition<IndexKeyPartType>(
        "IndexKeyPartType", {
            arrayOf(
                IndexKeyPartType.UUID,
                IndexKeyPartType.Reference,
                IndexKeyPartType.TypeId,
                IndexKeyPartType.Reversed
            )
        }
    )
}

internal val mapOfSimpleIndexKeyPartDefinitions: Map<IndexKeyPartType, IsValueDefinition<*, DefinitionsConversionContext>> =
    mapOf(
        IndexKeyPartType.UUID to EmbeddedObjectDefinition(dataModel = { UUIDKey.Model }),
        IndexKeyPartType.Reference to ContextualPropertyReferenceDefinition(
            contextualResolver = {
                it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
            }
        ),
        IndexKeyPartType.TypeId to EmbeddedObjectDefinition(dataModel = {
            @Suppress("UNCHECKED_CAST")
            TypeId.Model as DefinitionDataModel<TypeId<IndexedEnum<Any>>>
        }),
        IndexKeyPartType.Reversed to EmbeddedObjectDefinition(dataModel = { Reversed.Model })
    )

internal val mapOfIndexKeyPartDefinitions: Map<IndexKeyPartType, IsValueDefinition<*, DefinitionsConversionContext>> =
    mapOfSimpleIndexKeyPartDefinitions.plus(
        IndexKeyPartType.Multiple to EmbeddedObjectDefinition(dataModel = { Multiple.Model })
    )
