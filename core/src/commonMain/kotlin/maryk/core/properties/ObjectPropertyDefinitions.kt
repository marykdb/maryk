package maryk.core.properties

import maryk.core.models.AbstractObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.Values
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
    fun <T : Any, CX : IsPropertyContext, D : IsSerializableFlexBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        capturer: ((CX, T) -> Unit)? = null
    ) = FlexBytesDefinitionWrapper(index, name, definition, getter = getter, capturer = capturer).apply {
        addSingle(this)
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    internal fun <T : Any, TO : Any, CX : IsPropertyContext, D : IsSerializableFlexBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        toSerializable: (TO?, CX?) -> T?,
        fromSerializable: (T?) -> TO?,
        shouldSerialize: ((Any) -> Boolean)? = null,
        capturer: ((CX, T) -> Unit)? = null
    ) = FlexBytesDefinitionWrapper(
        index,
        name,
        definition,
        alternativeNames,
        getter,
        capturer,
        toSerializable,
        fromSerializable,
        shouldSerialize
    ).apply {
        addSingle(this)
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    internal fun <T : Any, TO : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        toSerializable: (TO?, CX?) -> T?,
        fromSerializable: (T?) -> TO?,
        shouldSerialize: ((Any) -> Boolean)? = null,
        capturer: ((CX, T) -> Unit)? = null
    ) = ContextualDefinitionWrapper(
        index,
        name,
        definition,
        alternativeNames,
        getter,
        capturer,
        toSerializable,
        fromSerializable,
        shouldSerialize
    ).apply {
        addSingle(this)
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, T) -> Unit)? = null
    ) = ContextualDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] and value [getter] with [toSerializable] and [fromSerializable] to transform values */
    internal fun <T : Any, TO : Any, CX : IsPropertyContext, D : IsSerializableFixedBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, T) -> Unit)? = null,
        toSerializable: (TO?, CX?) -> T?,
        fromSerializable: (T?) -> TO?
    ) = FixedBytesDefinitionWrapper(
        index,
        name,
        definition,
        alternativeNames,
        getter,
        capturer,
        toSerializable,
        fromSerializable
    ).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T : Any, CX : IsPropertyContext, D : IsSerializableFixedBytesEncodable<T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> T?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, T) -> Unit)? = null
    ) = FixedBytesDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] and value [getter] */
    fun <T : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: ListDefinition<T, CX>,
        getter: (DO) -> List<T>?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, List<T>) -> Unit)? = null
    ) = ListDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] and value [getter] */
    internal fun <T : Any, TO : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: ListDefinition<T, CX>,
        getter: (DO) -> List<TO>?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, List<T>) -> Unit)? = null,
        toSerializable: (TO) -> T,
        fromSerializable: (T) -> TO
    ) = ListDefinitionWrapper(
        index, name, definition, alternativeNames, getter, capturer,
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
    fun <T : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: SetDefinition<T, CX>,
        getter: (DO) -> Set<T>?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, Set<T>) -> Unit)? = null
    ) = SetDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /** Add map property [definition] with [name] and [index] and value [getter] */
    fun <K : Any, V : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: MapDefinition<K, V, CX>,
        getter: (DO) -> Map<K, V>?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, Map<K, V>) -> Unit)? = null
    ) = MapDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /**
     * Add map property [definition] with [name] and [index] and value [getter]
     * Also has a [toSerializable], [fromSerializable] and [capturer] to serialize and capture properties
     */
    fun <K : Any, V : Any, TO : Any, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: MapDefinition<K, V, CX>,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, Map<K, V>) -> Unit)? = null,
        toSerializable: (TO?, CX?) -> Map<K, V>?,
        fromSerializable: (Map<K, V>?) -> TO?
    ) = MapDefinitionWrapper(
        index, name, definition, alternativeNames, getter, capturer, toSerializable, fromSerializable
    ).apply {
        addSingle(this)
    }

    /** Add multi types property [definition] with [name] and [index] and value [getter] */
    fun <E : TypeEnum<T>, T: Any, TO : Any, CX : IsPropertyContext, D : IsMultiTypeDefinition<E, T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        capturer: ((CX, TypedValue<E, T>) -> Unit)? = null
    ) = MultiTypeDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    /**
     * Add multi types property [definition] with [name] and [index] and value [getter]
     * Also has a [toSerializable], [fromSerializable] and [capturer] to serialize and capture properties
     */
    fun <E : TypeEnum<T>, T: Any, TO : Any, CX : IsPropertyContext, D : IsMultiTypeDefinition<E, T, CX>> add(
        index: UInt,
        name: String,
        definition: D,
        getter: (DO) -> TO?,
        alternativeNames: Set<String>? = null,
        toSerializable: (TO?, CX?) -> TypedValue<E, T>?,
        fromSerializable: (TypedValue<E, T>?) -> TO?,
        capturer: ((CX, TypedValue<E, T>) -> Unit)? = null
    ) = MultiTypeDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer, toSerializable, fromSerializable).apply {
        addSingle(this)
    }

    /** Add embedded object property [definition] with [name] and [index] and value [getter] */
    fun <EODO : Any, P : ObjectPropertyDefinitions<EODO>, D : AbstractObjectDataModel<EODO, P, CXI, CX>, CXI : IsPropertyContext, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: IsEmbeddedObjectDefinition<EODO, P, D, CXI, CX>,
        getter: (DO) -> EODO? = { null },
        alternativeNames: Set<String>? = null,
        capturer: ((CXI, EODO) -> Unit)? = null
    ) = EmbeddedObjectDefinitionWrapper(index, name, definition, alternativeNames, getter, capturer).apply {
        addSingle(this)
    }

    @Suppress("UNCHECKED_CAST")
    /** Add embedded values property [definition] with [name] and [index] and value [getter] */
    fun <DM : IsValuesDataModel<P>, P : PropertyDefinitions, CX : IsPropertyContext> add(
        index: UInt,
        name: String,
        definition: IsEmbeddedValuesDefinition<DM, P, CX>,
        getter: (DO) -> Values<DM, P>? = { null },
        alternativeNames: Set<String>? = null,
        capturer: ((CX, Values<DM, P>) -> Unit)? = null
    ) = EmbeddedValuesDefinitionWrapper(
        index, name, definition, alternativeNames,
        getter as (Any) -> Values<DM, P>?,
        capturer
    ).apply {
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
    override val capturer: (DefinitionsConversionContext?, ObjectPropertyDefinitions<Any>) -> Unit
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
            capturer(context, this)
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

    override val toSerializable: ((ObjectPropertyDefinitions<Any>?, DefinitionsConversionContext?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val fromSerializable: ((ObjectPropertyDefinitions<Any>?) -> ObjectPropertyDefinitions<Any>?)? = null
    override val shouldSerialize: ((Any) -> Boolean)? = null
    override val capturer: ((DefinitionsConversionContext, ObjectPropertyDefinitions<Any>) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
