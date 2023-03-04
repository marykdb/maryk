package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.json.ArrayType
import maryk.json.TokenType
import maryk.json.ValueType
import maryk.json.ValueType.IsNullValueType
import kotlin.native.concurrent.SharedImmutable
import maryk.core.properties.definitions.index.Multiple as MultipleInstance
import maryk.core.properties.definitions.index.Reversed as ReversedInstance

/** Indexed type of property definitions */
sealed class IndexKeyPartType<out T: IsIndexable>(
    index: UInt
) : IndexedEnumImpl<IndexKeyPartType<*>>(index), TokenType, IsCoreEnum, TypeEnum<T> {
    object UUID : IndexKeyPartType<UUIDKey>(1u), IsNullValueType
    object Reference : ValueType<String>, IndexKeyPartType<IsIndexablePropertyReference<*>>(2u) {
        override val name = "Ref"
    }
    object Reversed : IndexKeyPartType<ReversedInstance<*>>(3u), ValueType<String>
    object Multiple : IndexKeyPartType<MultipleInstance>(4u), ArrayType

    companion object : IndexedEnumDefinition<IndexKeyPartType<*>>(
        IndexKeyPartType::class, {
            arrayOf(UUID, Reference, Reversed, Multiple)
        }
    )
}

@SharedImmutable
internal val mapOfSimpleIndexKeyPartDefinitions: Map<IndexKeyPartType<IsIndexable>, IsValueDefinition<*, DefinitionsConversionContext>> =
    mapOf(
        IndexKeyPartType.UUID to EmbeddedObjectDefinition(dataModel = { UUIDKey.Model }),
        IndexKeyPartType.Reference to ContextualPropertyReferenceDefinition(
            contextualResolver = {
                it?.propertyDefinitions as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
            }
        ),
        IndexKeyPartType.Reversed to EmbeddedObjectDefinition(dataModel = { ReversedInstance.Model.Model })
    )

@SharedImmutable
internal val mapOfIndexKeyPartDefinitions: Map<IndexKeyPartType<*>, IsValueDefinition<*, DefinitionsConversionContext>> =
    mapOfSimpleIndexKeyPartDefinitions.plus(
        IndexKeyPartType.Multiple to EmbeddedObjectDefinition(dataModel = { MultipleInstance.Model.Model })
    )
