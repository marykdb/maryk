package maryk.core.properties

import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsConversionContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** A collection of Property Definitions which can be used to model a ObjectDataModel */
abstract class PropertyDefinitions : AbstractPropertyDefinitions<Any>(), IsValuesPropertyDefinitions

internal class MutableRootModel : MutablePropertyDefinitions(), IsRootModel {
    override val Model: IsRootDataModel<out IsValuesPropertyDefinitions> get() = super.Model as IsRootDataModel<out IsValuesPropertyDefinitions>
}

internal class MutableModel : MutablePropertyDefinitions(), IsRootModel {
    override val Model: IsRootDataModel<out IsValuesPropertyDefinitions> get() = super.Model as IsRootDataModel<out IsValuesPropertyDefinitions>
}

/** Mutable variant of ObjectPropertyDefinitions for a IsCollectionDefinition implementation */
internal abstract class MutablePropertyDefinitions : PropertyDefinitions(), IsMutablePropertyDefinitions<AnyDefinitionWrapper> {
    internal var _model: IsValuesDataModel<*>? = null

    override val Model: IsValuesDataModel<*>
        get() = _model ?: throw Exception("No Model yet set, likely DataModel was not initialized yet")

    override fun add(element: AnyDefinitionWrapper): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<AnyDefinitionWrapper>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: AnyDefinitionWrapper) = false
    override fun removeAll(elements: Collection<AnyDefinitionWrapper>) = false
    override fun retainAll(elements: Collection<AnyDefinitionWrapper>) = false
}

/** Definition for a collection of Property Definitions for in a ObjectPropertyDefinitions */
internal data class PropertyDefinitionsCollectionDefinition(
    val isRootModel: Boolean,
    override val capturer: Unit.(DefinitionsConversionContext?, PropertyDefinitions) -> Unit
) : IsCollectionDefinition<
    AnyDefinitionWrapper,
    PropertyDefinitions,
    DefinitionsConversionContext,
    EmbeddedObjectDefinition<
        AnyDefinitionWrapper,
        ObjectPropertyDefinitions<AnyDefinitionWrapper>,
        SimpleObjectDataModel<
                AnyDefinitionWrapper,
                ObjectPropertyDefinitions<AnyDefinitionWrapper>
        >,
        IsPropertyContext,
        IsPropertyContext
    >
>, IsPropertyDefinitionsCollectionDefinition<PropertyDefinitions> {
    override val required = true
    override val final = true
    override val minSize: UInt? = null
    override val maxSize: UInt? = null

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsDefinitionWrapper.Model as SimpleObjectDataModel<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<PropertyDefinitions, IsPropertyDefinition<PropertyDefinitions>, *>?,
        newValue: PropertyDefinitions,
        validator: (item: AnyDefinitionWrapper, itemRefFactory: () -> IsPropertyReference<AnyDefinitionWrapper, IsPropertyDefinition<AnyDefinitionWrapper>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsConversionContext?): MutablePropertyDefinitions =
        when (isRootModel) {
            true -> MutableRootModel()
            else -> MutableModel()
        }.apply {
            capturer(Unit, context, this)
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
            if (reader.currentToken !is StartObject) {
                throw ParseException("Property definitions should be an Object")
            }
            val collection = newMutableCollection(context)

            while (reader.nextToken() !== EndObject) {
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
internal data class PropertyDefinitionsCollectionDefinitionWrapper<in DO : Any>(
    override val index: UInt,
    override val name: String,
    override val definition: PropertyDefinitionsCollectionDefinition,
    override val getter: (DO) -> PropertyDefinitions?,
    override val alternativeNames: Set<String>? = null
) :
    IsCollectionDefinition<AnyDefinitionWrapper, PropertyDefinitions, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>, SimpleObjectDataModel<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>>, IsPropertyContext, IsPropertyContext>> by definition,
    IsDefinitionWrapper<PropertyDefinitions, PropertyDefinitions, DefinitionsConversionContext, DO>
{
    override val graphType = PropRef

    override val toSerializable: (Unit.(PropertyDefinitions?, DefinitionsConversionContext?) -> PropertyDefinitions?)? = null
    override val fromSerializable: (Unit.(PropertyDefinitions?) -> PropertyDefinitions?)? = null
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
    override val capturer: (Unit.(DefinitionsConversionContext, PropertyDefinitions) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
