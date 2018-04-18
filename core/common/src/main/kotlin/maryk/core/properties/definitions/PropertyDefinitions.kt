package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.objects.AbstractDataModel
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** A collection of Property Definitions which can be used to model a DataModel */
abstract class PropertyDefinitions<DO: Any>(
    properties: MutableList<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> = mutableListOf()
) : Collection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> {
    override fun iterator() = _allProperties.iterator()

    private val _allProperties: MutableList<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> = mutableListOf()

    private val indexToDefinition = mutableMapOf<Int, IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>()
    private val nameToDefinition = mutableMapOf<String, IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>()

    // Implementation of Collection
    override val size = _allProperties.size
    override fun contains(element: IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>) =
        this._allProperties.contains(element)
    override fun containsAll(elements: Collection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>) =
        this._allProperties.containsAll(elements)
    override fun isEmpty() = this._allProperties.isEmpty()

    /** Get the definition with a property [name] */
    fun getDefinition(name: String) = nameToDefinition[name]
    /** Get the definition with a property [index] */
    fun getDefinition(index: Int) = indexToDefinition[index]

    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String) = nameToDefinition[name]?.getter
    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: Int) = indexToDefinition[index]?.getter

    init {
        for (it in properties) {
            addSingle(it)
        }
    }

    /** Add a single property definition wrapper */
    internal fun addSingle(propertyDefinitionWrapper: IsPropertyDefinitionWrapper<out Any, *, DO>) {
        @Suppress("UNCHECKED_CAST")
        _allProperties.add(propertyDefinitionWrapper as IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>)

        require(propertyDefinitionWrapper.index in (0..Short.MAX_VALUE), { "${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} is outside range $(0..Short.MAX_VALUE)" })
        require(indexToDefinition[propertyDefinitionWrapper.index] == null, { "Duplicate index ${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} and ${indexToDefinition[propertyDefinitionWrapper.index]?.name}" })
        indexToDefinition[propertyDefinitionWrapper.index] = propertyDefinitionWrapper
        nameToDefinition[propertyDefinitionWrapper.name] = propertyDefinitionWrapper
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> T? = { null }
    ) = PropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D,
        getter: (DO) -> T? = { null }
    ) = FixedBytesPropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: ListDefinition<T, CX>,
        getter: (DO) -> List<T>? = { null }
    ) = ListPropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add set property [definition] with [name] and [index] and value [getter] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: SetDefinition<T, CX>,
        getter: (DO) -> Set<T>? = { null }
    ) = SetPropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add map property [definition] with [name] and [index] and value [getter] */
    protected fun <K: Any, V: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: MapDefinition<K, V, CX>,
        getter: (DO) -> Map<K, V>? = { null }
    ) = MapPropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add multi type property [definition] with [name] and [index] and value [getter] */
    protected fun <E: IndexedEnum<E>, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: MultiTypeDefinition<E, CX>,
        getter: (DO) -> TypedValue<E, *>? = { null }
    ) = PropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Add sub model property [definition] with [name] and [index] and value [getter] */
    fun <SDO: Any, P: PropertyDefinitions<SDO>, D: AbstractDataModel<SDO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: SubModelDefinition<SDO, P, D, CXI, CX>,
        getter: (DO) -> SDO? = { null }
    ) = SubModelPropertyDefinitionWrapper(index, name, definition, getter).apply {
        addSingle(this)
    }

    /** Get PropertyReference by [referenceName] */
    fun getPropertyReferenceByName(referenceName: String): IsPropertyReference<*, IsPropertyDefinition<*>> {
        val names = referenceName.split(".")

        var propertyReference: IsPropertyReference<*, *>? = null
        for (name in names) {
            propertyReference = when (propertyReference) {
                null -> this.getDefinition(name)?.getRef(propertyReference)
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbedded(name)
                else -> throw DefNotFoundException("Illegal $referenceName, ${propertyReference.completeName} does not contain embedded property definitions for $name")
            } ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")
        }

        return propertyReference ?: throw DefNotFoundException("Property reference «$referenceName» does not exist")
    }

    /** Get PropertyReference by bytes from [reader] with [length] */
    fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
        var readLength = 0

        val lengthReader = {
            readLength++
            reader()
        }

        var propertyReference: IsPropertyReference<*, *>? = null
        while (readLength < length) {
            propertyReference = when (propertyReference) {
                null -> {
                    val index = initIntByVar(lengthReader)
                    this.getDefinition(index)?.getRef(propertyReference)
                }
                is HasEmbeddedPropertyReference<*> -> propertyReference.getEmbeddedRef(lengthReader)
                else -> throw DefNotFoundException("More property references found on property that cannot have any ")
            } ?: throw DefNotFoundException("Property reference does not exist")
        }

        return propertyReference ?: throw DefNotFoundException("Property reference does not exist")
    }
}

/** Mutable variant of PropertyDefinitions for a IsCollectionDefinition implementation */
private class MutablePropertyDefinitions<DO: Any> : PropertyDefinitions<DO>(), MutableCollection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> {
    override fun add(element: IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>): Boolean {
        this.addSingle(propertyDefinitionWrapper = element)
        return true
    }

    override fun addAll(elements: Collection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>): Boolean {
        elements.forEach {
            this.addSingle(it)
        }
        return true
    }

    override fun clear() {}
    override fun remove(element: IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>) = false
    override fun removeAll(elements: Collection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>) = false
    override fun retainAll(elements: Collection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>) = false
}

/** Definition for a collection of Property Definitions for in a PropertyDefinitions */
internal data class PropertyDefinitionsCollectionDefinition(
    private val capturer: (DataModelContext?, PropertyDefinitions<Any>) -> Unit
) : IsCollectionDefinition<
        IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>,
        PropertyDefinitions<Any>,
        DataModelContext,
        SubModelDefinition<
                IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>,
                PropertyDefinitions<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>,
                SimpleDataModel<
                        IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>,
                        PropertyDefinitions<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>
                        >,
                IsPropertyContext,
                IsPropertyContext
        >
> {
    override val indexed = false
    override val searchable = false
    override val required = true
    override val final = true
    override val minSize: Int? = null
    override val maxSize: Int? = null
    override val propertyDefinitionType = PropertyDefinitionType.List

    override val valueDefinition = SubModelDefinition(
        dataModel = {
            @Suppress("UNCHECKED_CAST")
            IsPropertyDefinitionWrapper.Model as SimpleDataModel<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, PropertyDefinitions<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>>
        }
    )

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<PropertyDefinitions<Any>, IsPropertyDefinition<PropertyDefinitions<Any>>>?,
        newValue: PropertyDefinitions<Any>,
        validator: (item: IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, itemRefFactory: () -> IsPropertyReference<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, IsPropertyDefinition<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>>?) -> Any
    ) {}

    override fun newMutableCollection(context: DataModelContext?): MutableCollection<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>> {
        return MutablePropertyDefinitions<Any>().apply {
            capturer(context, this)
        }
    }

    /**
     * Overridden to render definitions list in YAML as objects
     */
    override fun writeJsonValue(
        value: PropertyDefinitions<Any>,
        writer: IsJsonLikeWriter,
        context: DataModelContext?
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

    override fun readJson(reader: IsJsonLikeReader, context: DataModelContext?): PropertyDefinitions<Any> {
        if (reader is IsYamlReader) {
            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("Property definitions should be an Object")
            }
            val collection = newMutableCollection(context) as MutablePropertyDefinitions<Any>

            while (reader.nextToken() !== JsonToken.EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
            return collection
        } else {
            return super.readJson(reader, context)
        }
    }
}

/** Wrapper specifically to wrap a PropertyDefinitionsCollectionDefinition */
internal data class PropertyDefinitionsCollectionDefinitionWrapper<in DO: Any>(
    override val index: Int,
    override val name: String,
    override val definition: PropertyDefinitionsCollectionDefinition,
    override val getter: (DO) -> PropertyDefinitions<Any>?
) :
    IsCollectionDefinition<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, PropertyDefinitions<Any>, DataModelContext, SubModelDefinition<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, PropertyDefinitions<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>, SimpleDataModel<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>, PropertyDefinitions<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>>, IsPropertyContext, IsPropertyContext>> by definition,
    IsPropertyDefinitionWrapper<PropertyDefinitions<Any>, DataModelContext, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) = throw Throwable("Not implemented")
}
