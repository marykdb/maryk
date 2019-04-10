package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.query.DefinitionsConversionContext
import maryk.json.ArrayType
import maryk.json.TokenType
import maryk.json.ValueType

/** Indexed type of property definitions */
sealed class IndexKeyPartType(
    index: UInt
) : IndexedEnumImpl<IndexKeyPartType>(index), TokenType, IsCoreEnum {
    object UUID : IndexKeyPartType(1u), ValueType.IsNullValueType
    object Reference : ValueType<String>, IndexKeyPartType(2u) {
        override val name = "Ref"
    }
    object Reversed : IndexKeyPartType(3u), ValueType<String>
    object Multiple : IndexKeyPartType(4u), ArrayType

    companion object : IndexedEnumDefinition<IndexKeyPartType>(
        IndexKeyPartType::class, {
            arrayOf(UUID, Reference, Reversed, Multiple)
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
        IndexKeyPartType.Reversed to EmbeddedObjectDefinition(dataModel = { Reversed.Model })
    )

internal val mapOfIndexKeyPartDefinitions: Map<IndexKeyPartType, IsValueDefinition<*, DefinitionsConversionContext>> =
    mapOfSimpleIndexKeyPartDefinitions.plus(
        IndexKeyPartType.Multiple to EmbeddedObjectDefinition(dataModel = { Multiple.Model })
    )
