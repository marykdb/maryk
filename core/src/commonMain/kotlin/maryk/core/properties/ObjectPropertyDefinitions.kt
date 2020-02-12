package maryk.core.properties

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
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
abstract class ObjectPropertyDefinitions<DO : Any> : AbstractPropertyDefinitions<DO>() {
    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String): ((DO) -> Any?)? = { nameToDefinition[name]?.getPropertyAndSerialize(it, null) }

    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: UInt): ((DO) -> Any?)? = { indexToDefinition[index]?.getPropertyAndSerialize(it, null) }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        alternativeNames: Set<String>? = null,
        capturer: (Unit.(CX, T) -> Unit)? = null
    ) = ContextualDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }
}

/** Mutable variant of ObjectPropertyDefinitions for a IsCollectionDefinition implementation */
internal class MutableObjectPropertyDefinitions<DO: Any> : ObjectPropertyDefinitions<DO>(), IsMutablePropertyDefinitions<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override fun add(element: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>) = false
    override fun removeAll(elements: Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) = false
    override fun retainAll(elements: Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) = false
}

/** Definition for a collection of Property Definitions for in a ObjectPropertyDefinitions */
internal data class ObjectPropertyDefinitionsCollectionDefinition(
    override val capturer: Unit.(DefinitionsConversionContext?, ObjectPropertyDefinitions<Any>) -> Unit
) : IsCollectionDefinition<
        AnyDefinitionWrapper,
        ObjectPropertyDefinitions<Any>,
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
>, IsPropertyDefinitionsCollectionDefinition<ObjectPropertyDefinitions<Any>> {
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
        refGetter: () -> IsPropertyReference<ObjectPropertyDefinitions<Any>, IsPropertyDefinition<ObjectPropertyDefinitions<Any>>, *>?,
        newValue: ObjectPropertyDefinitions<Any>,
        validator: (item: AnyDefinitionWrapper, itemRefFactory: () -> IsPropertyReference<AnyDefinitionWrapper, IsPropertyDefinition<AnyDefinitionWrapper>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsConversionContext?) =
        MutableObjectPropertyDefinitions<Any>().apply {
            capturer(Unit, context, this)
        }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: ObjectPropertyDefinitions<Any>,
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

    override fun readJson(
        reader: IsJsonLikeReader,
        context: DefinitionsConversionContext?
    ): ObjectPropertyDefinitions<Any> {
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

/** Wrapper specifically to wrap a ObjectPropertyDefinitionsCollectionDefinition */
internal data class ObjectPropertyDefinitionsCollectionDefinitionWrapper<in DO : Any>(
    override val index: UInt,
    override val name: String,
    override val definition: ObjectPropertyDefinitionsCollectionDefinition,
    override val getter: (DO) -> ObjectPropertyDefinitions<Any>?,
    override val alternativeNames: Set<String>? = null
) :
    IsCollectionDefinition<AnyDefinitionWrapper, ObjectPropertyDefinitions<Any>, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>, SimpleObjectDataModel<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>>, IsPropertyContext, IsPropertyContext>> by definition,
    IsDefinitionWrapper<ObjectPropertyDefinitions<Any>, ObjectPropertyDefinitions<Any>, DefinitionsConversionContext, DO>
{
    override val graphType = PropRef

    override val toSerializable: (Unit.(ObjectPropertyDefinitions<Any>?, DefinitionsConversionContext?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val fromSerializable: (Unit.(ObjectPropertyDefinitions<Any>?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
    override val capturer: (Unit.(DefinitionsConversionContext, ObjectPropertyDefinitions<Any>) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
