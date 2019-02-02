package maryk.core.properties

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.wrapper.AnyPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** A collection of Property Definitions which can be used to model a ObjectDataModel */
abstract class PropertyDefinitions : AbstractPropertyDefinitions<Any>()

/** Mutable variant of ObjectPropertyDefinitions for a IsCollectionDefinition implementation */
internal class MutablePropertyDefinitions : PropertyDefinitions(), IsMutablePropertyDefinitions<AnyPropertyDefinitionWrapper> {
    override fun add(element: AnyPropertyDefinitionWrapper): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<AnyPropertyDefinitionWrapper>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: AnyPropertyDefinitionWrapper) = false
    override fun removeAll(elements: Collection<AnyPropertyDefinitionWrapper>) = false
    override fun retainAll(elements: Collection<AnyPropertyDefinitionWrapper>) = false
}

/** Definition for a collection of Property Definitions for in a ObjectPropertyDefinitions */
internal data class PropertyDefinitionsCollectionDefinition(
    override val capturer: (DefinitionsConversionContext?, PropertyDefinitions) -> Unit
) : IsCollectionDefinition<
    AnyPropertyDefinitionWrapper,
    PropertyDefinitions,
    DefinitionsConversionContext,
    EmbeddedObjectDefinition<
        AnyPropertyDefinitionWrapper,
        ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>,
        SimpleObjectDataModel<
                AnyPropertyDefinitionWrapper,
                ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>
        >,
        IsPropertyContext,
        IsPropertyContext
    >
>, IsPropertyDefinitionsCollectionDefinition<PropertyDefinitions> {
    override val indexed = false
    override val required = true
    override val final = true
    override val minSize: UInt? = null
    override val maxSize: UInt? = null
    override val propertyDefinitionType = PropertyDefinitionType.List

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsPropertyDefinitionWrapper.Model as SimpleObjectDataModel<AnyPropertyDefinitionWrapper, ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<PropertyDefinitions, IsPropertyDefinition<PropertyDefinitions>, *>?,
        newValue: PropertyDefinitions,
        validator: (item: AnyPropertyDefinitionWrapper, itemRefFactory: () -> IsPropertyReference<AnyPropertyDefinitionWrapper, IsPropertyDefinition<AnyPropertyDefinitionWrapper>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsConversionContext?) =
        MutablePropertyDefinitions().apply {
            capturer(context, this)
        }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: PropertyDefinitions,
        writer: IsJsonLikeWriter,
        context: DefinitionsConversionContext?
    ) {
        if (writer is YamlWriter) {
            writer.writeStartObject()
            for (it in value) {
                valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndObject()
        } else {
            super.writeJsonValue(value, writer, context)
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: DefinitionsConversionContext?): PropertyDefinitions {
        return if (reader is IsYamlReader) {
            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("Property definitions should be an Object")
            }
            val collection = newMutableCollection(context)

            while (reader.nextToken() !== JsonToken.EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
            collection
        } else {
            super.readJson(reader, context)
        }
    }
}

/** Wrapper specifically to wrap a PropertyDefinitionsCollectionDefinition */
internal data class PropertyDefinitionsCollectionDefinitionWrapper<in DO: Any>(
    override val index: Int,
    override val name: String,
    override val definition: PropertyDefinitionsCollectionDefinition,
    override val getter: (DO) -> PropertyDefinitions?
) :
    IsCollectionDefinition<AnyPropertyDefinitionWrapper, PropertyDefinitions, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyPropertyDefinitionWrapper, ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>, SimpleObjectDataModel<AnyPropertyDefinitionWrapper, ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>>, IsPropertyContext, IsPropertyContext>> by definition,
    IsPropertyDefinitionWrapper<PropertyDefinitions, PropertyDefinitions, DefinitionsConversionContext, DO>
{
    override val graphType = PropRefGraphType.PropRef

    override val toSerializable: ((PropertyDefinitions?, DefinitionsConversionContext?) -> PropertyDefinitions?)? = null
    override val fromSerializable: ((PropertyDefinitions?) -> PropertyDefinitions?)? = null
    override val shouldSerialize: ((Any) -> Boolean)? = null
    override val capturer: ((DefinitionsConversionContext, PropertyDefinitions) -> Unit)? = null

    override fun getRef(parentRef: AnyPropertyReference?) = throw Throwable("Not implemented")
}
