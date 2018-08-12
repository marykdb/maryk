package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DefinitionsContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** A collection of Property Definitions which can be used to model a ObjectDataModel */
abstract class ObjectPropertyDefinitions<DO: Any>(
    properties: MutableList<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>> = mutableListOf()
) : AbstractPropertyDefinitions<DO>(properties) {
    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String): ((DO) -> Any?)? = { nameToDefinition[name]?.getPropertyAndSerialize(it, null) }
    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: Int): ((DO) -> Any?)? = { indexToDefinition[index]?.getPropertyAndSerialize(it, null) }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    internal fun <T: Any, TO: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        toSerializable: (TO?, CX?) -> T?,
        fromSerializable: (T?) -> TO?,
        capturer: ((CX, T) -> Unit)? = null
    ) = PropertyDefinitionWrapper(index, name, definition, getter, capturer, toSerializable, fromSerializable).apply {
        addSingle(this)
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        capturer: ((CX, T) -> Unit)? = null
    ) = PropertyDefinitionWrapper(index, name, definition, getter, capturer).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] and value [getter] with [toSerializable] and [fromSerializable] to transform values */
    internal fun <T: Any, TO: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        capturer: ((CX, T) -> Unit)? = null,
        toSerializable: (TO?, CX?) -> T?,
        fromSerializable: (T?) -> TO?
    ) = FixedBytesPropertyDefinitionWrapper(
        index,
        name,
        definition,
        getter,
        capturer,
        toSerializable,
        fromSerializable
    ).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        capturer: ((CX, T) -> Unit)? = null
    ) = FixedBytesPropertyDefinitionWrapper(index, name, definition, getter, capturer = capturer).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: ListDefinition<T, CX>,
        getter: (DO) -> List<T>?,
        capturer: ((CX, List<T>) -> Unit)? = null
    ) = ListPropertyDefinitionWrapper(index, name, definition, getter, capturer).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] and value [getter] */
    internal fun <T: Any, TO:Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: ListDefinition<T, CX>,
        getter: (DO) -> List<TO>?,
        capturer: ((CX, List<T>) -> Unit)? = null,
        toSerializable: (TO) -> T,
        fromSerializable: (T) -> TO
    ) = ListPropertyDefinitionWrapper(
        index, name, definition, getter, capturer,
        toSerializable = { value, _ ->
            value?.map { toSerializable(it) }
        },
        fromSerializable = { value: List<T>? ->
            value?.map { fromSerializable(it) }
        }
    ).apply {
        addSingle(this)
    }

    /** Add set property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: SetDefinition<T, CX>,
        getter: (DO) -> Set<T>?,
        capturer: ((CX, Set<T>) -> Unit)? = null
    ) = SetPropertyDefinitionWrapper(index, name, definition, getter, capturer).apply {
        addSingle(this)
    }

    /** Add map property [definition] with [name] and [index] and value [getter] */
    fun <K: Any, V: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: MapDefinition<K, V, CX>,
        getter: (DO) -> Map<K, V>?,
        capturer: ((CX, Map<K, V>) -> Unit)? = null
    ) = MapPropertyDefinitionWrapper(index, name, definition, getter, capturer).apply {
        addSingle(this)
    }

    /**
     * Add map property [definition] with [name] and [index] and value [getter]
     * Also has a [toSerializable], [fromSerializable] and [capturer] to serialize and capture properties
     */
    fun <K: Any, V: Any, TO: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: MapDefinition<K, V, CX>,
        getter: (DO) -> TO?,
        capturer: ((CX, Map<K, V>) -> Unit)? = null,
        toSerializable: (TO?, CX?) -> Map<K, V>?,
        fromSerializable: (Map<K, V>?) -> TO?
    ) = MapPropertyDefinitionWrapper(
        index, name, definition, getter, capturer, toSerializable, fromSerializable
    ).apply {
        addSingle(this)
    }

    /** Add embedded object property [definition] with [name] and [index] and value [getter] */
    fun <EODO: Any, P: ObjectPropertyDefinitions<EODO>, D: AbstractObjectDataModel<EODO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: IsEmbeddedObjectDefinition<EODO, P, D, CXI, CX>,
        getter: (DO) -> EODO? = { null },
        capturer: ((CXI, EODO) -> Unit)? = null
    ) = EmbeddedObjectPropertyDefinitionWrapper(index, name, definition, getter, capturer).apply {
        addSingle(this)
    }
}

/** Mutable variant of ObjectPropertyDefinitions for a IsCollectionDefinition implementation */
internal class MutableObjectPropertyDefinitions<DO: Any> : ObjectPropertyDefinitions<DO>(), MutableCollection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    override fun add(element: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>) = false
    override fun removeAll(elements: Collection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) = false
    override fun retainAll(elements: Collection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) = false
}

/** Definition for a collection of Property Definitions for in a ObjectPropertyDefinitions */
internal data class ObjectPropertyDefinitionsCollectionDefinition(
    private val capturer: (DefinitionsContext?, ObjectPropertyDefinitions<Any>) -> Unit
) : IsCollectionDefinition<
        IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>,
        ObjectPropertyDefinitions<Any>,
        DefinitionsContext,
        EmbeddedObjectDefinition<
                IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>,
                ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>,
                SimpleObjectDataModel<
                        IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>,
                        ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>
                >,
                IsPropertyContext,
                IsPropertyContext
        >
> {
    override val indexed = false
    override val required = true
    override val final = true
    override val minSize: Int? = null
    override val maxSize: Int? = null
    override val propertyDefinitionType = PropertyDefinitionType.List

    override val valueDefinition = EmbeddedObjectDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsPropertyDefinitionWrapper.Model as SimpleObjectDataModel<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<ObjectPropertyDefinitions<Any>, IsPropertyDefinition<ObjectPropertyDefinitions<Any>>, *>?,
        newValue: ObjectPropertyDefinitions<Any>,
        validator: (item: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, itemRefFactory: () -> IsPropertyReference<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, IsPropertyDefinition<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>, *>?) -> Any
    ) {}

    override fun newMutableCollection(context: DefinitionsContext?) =
        MutableObjectPropertyDefinitions<Any>().apply {
            capturer(context, this)
        }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: ObjectPropertyDefinitions<Any>,
        writer: IsJsonLikeWriter,
        context: DefinitionsContext?
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

    override fun readJson(reader: IsJsonLikeReader, context: DefinitionsContext?): ObjectPropertyDefinitions<Any> {
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

/** Wrapper specifically to wrap a ObjectPropertyDefinitionsCollectionDefinition */
internal data class ObjectPropertyDefinitionsCollectionDefinitionWrapper<in DO: Any>(
    override val index: Int,
    override val name: String,
    override val definition: ObjectPropertyDefinitionsCollectionDefinition,
    override val getter: (DO) -> ObjectPropertyDefinitions<Any>?
) :
    IsCollectionDefinition<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<Any>, DefinitionsContext, EmbeddedObjectDefinition<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>, SimpleObjectDataModel<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>, ObjectPropertyDefinitions<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, Any>>>, IsPropertyContext, IsPropertyContext>> by definition,
    IsPropertyDefinitionWrapper<ObjectPropertyDefinitions<Any>, ObjectPropertyDefinitions<Any>, DefinitionsContext, DO>
{
    override val graphType = PropRefGraphType.PropRef

    override val toSerializable: ((ObjectPropertyDefinitions<Any>?, DefinitionsContext?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val fromSerializable: ((ObjectPropertyDefinitions<Any>?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val capturer: ((DefinitionsContext, ObjectPropertyDefinitions<Any>) -> Unit)? = null

    override fun getRef(parentRef: AnyPropertyReference?) = throw Throwable("Not implemented")
}
