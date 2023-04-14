package maryk.core.models

import maryk.core.models.definitions.IsDataModelDefinition
import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Base class for all Object based DataModels.
 * Implements IsObjectDataModel and provides methods to get properties by name or index.
 */
abstract class BaseObjectDataModel<DO : Any> : BaseDataModel<DO>(), IsObjectDataModel<DO> {
    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String): ((DO) -> Any?)? = nameToDefinition[name]?.run { { getPropertyAndSerialize(it, null) } }

    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: UInt): ((DO) -> Any?)? = indexToDefinition[index]?.run { { getPropertyAndSerialize(it, null) } }
}

internal abstract class BaseMutableObjectDataModel<DO: Any> : BaseObjectDataModel<DO>(),
    IsMutableDataModel<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
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

internal class MutableValueDataModel<DO: ValueDataObject>: BaseMutableObjectDataModel<DO>(),
    IsValueDataModel<DO, MutableValueDataModel<DO>> {
    internal var _model: IsDataModelDefinition? = null
    override val Serializer = ValueDataModelSerializer(this)

    override val Meta get() = _model as? ValueDataModelDefinition
        ?: throw Exception("No Model yet set, likely DataModel was not initialized yet")

    override fun invoke(values: ObjectValues<DO, MutableValueDataModel<DO>>): DO {
        @Suppress("UNCHECKED_CAST")
        return ValueDataObjectWithValues(this.toBytes(values), values) as DO
    }
}

/** Definition for a collection of Property Definitions for in a ObjectPropertyDefinitions */
internal data class ObjectDataModelPropertiesCollectionDefinition(
    override val capturer: Unit.(DefinitionsConversionContext?, IsObjectDataModel<Any>) -> Unit
) : IsCollectionDefinition<
        AnyDefinitionWrapper,
        IsObjectDataModel<Any>,
        DefinitionsConversionContext,
        EmbeddedObjectDefinition<
                AnyDefinitionWrapper,
                IsTypedObjectDataModel<AnyDefinitionWrapper, *, IsPropertyContext, IsPropertyContext>,
                IsPropertyContext,
                IsPropertyContext
        >
>, IsDataModelPropertiesCollectionDefinition<IsObjectDataModel<Any>> {
    override val required = true
    override val final = true
    override val minSize: UInt? = null
    override val maxSize: UInt? = null

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsDefinitionWrapper.Model as IsTypedObjectDataModel<AnyDefinitionWrapper, *, IsPropertyContext, IsPropertyContext>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<IsObjectDataModel<Any>, IsPropertyDefinition<IsObjectDataModel<Any>>, *>?,
        newValue: IsObjectDataModel<Any>,
        validator: (item: AnyDefinitionWrapper, itemRefFactory: () -> IsPropertyReference<AnyDefinitionWrapper, IsPropertyDefinition<AnyDefinitionWrapper>, *>?) -> Any
    ) {}

    @Suppress("UNCHECKED_CAST")
    override fun newMutableCollection(context: DefinitionsConversionContext?) =
        MutableValueDataModel<ValueDataObject>().apply {
            capturer(Unit, context, this as IsObjectDataModel<Any>)
        } as BaseMutableObjectDataModel<Any>

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: IsObjectDataModel<Any>,
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
    ): IsObjectDataModel<Any> {
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

/** Wrapper specifically to wrap a ObjectDataModelCollectionDefinition */
internal data class ObjectDataModelCollectionDefinitionWrapper<in DO : Any>(
    override val index: UInt,
    override val name: String,
    override val definition: ObjectDataModelPropertiesCollectionDefinition,
    override val getter: (DO) -> IsObjectDataModel<Any>?,
    override val alternativeNames: Set<String>? = null
) :
    IsCollectionDefinition<AnyDefinitionWrapper, IsObjectDataModel<Any>, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyDefinitionWrapper, IsTypedObjectDataModel<AnyDefinitionWrapper, *, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>> by definition,
    IsDefinitionWrapper<IsObjectDataModel<Any>, IsObjectDataModel<Any>, DefinitionsConversionContext, DO>
{
    override val graphType = PropRef

    override val toSerializable: (Unit.(IsObjectDataModel<Any>?, DefinitionsConversionContext?) -> IsObjectDataModel<Any>?)? = null
    override val fromSerializable: (Unit.(IsObjectDataModel<Any>?) -> IsObjectDataModel<Any>?)? = null
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
    override val capturer: (Unit.(DefinitionsConversionContext, IsObjectDataModel<Any>) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
