package maryk.core.properties

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializableFixedBytesEncodable
import maryk.core.properties.definitions.IsSerializableFlexBytesEncodable
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedValuesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.references.HasEmbeddedPropertyReference
import maryk.core.properties.references.IsPropertyReference

abstract class AbstractPropertyDefinitions<DO: Any>(
    properties: MutableList<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>> = mutableListOf()
) :
    IsPropertyDefinitions,
    Collection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>
{
    override fun iterator() = _allProperties.iterator()

    private val _allProperties: MutableList<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>> = mutableListOf()

    protected val indexToDefinition = mutableMapOf<Int, IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()
    protected val nameToDefinition = mutableMapOf<String, IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>()

    // Implementation of Collection
    override val size = _allProperties.size
    override fun contains(element: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>) =
        this._allProperties.contains(element)
    override fun containsAll(elements: Collection<IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>>) =
        this._allProperties.containsAll(elements)
    override fun isEmpty() = this._allProperties.isEmpty()

    /** Get the definition with a property [name] */
    fun getDefinition(name: String) = nameToDefinition[name]
    /** Get the definition with a property [index] */
    fun getDefinition(index: Int) = indexToDefinition[index]

    init {
        for (it in properties) {
            addSingle(it)
        }
    }

    /** Add flex bytes encodable property [definition] with [name] and [index] */
    internal fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D
    ) = PropertyDefinitionWrapper<T, T, CX, D, Any>(index, name, definition).apply {
        addSingle(this)
    }

    /** Add fixed bytes encodable property [definition] with [name] and [index] */
    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
        index: Int,
        name: String,
        definition: D
    ) = FixedBytesPropertyDefinitionWrapper<T, T, CX, D, Any>(index, name, definition).apply {
        addSingle(this)
    }

    /** Add list property [definition] with [name] and [index] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: ListDefinition<T, CX>
    ) = ListPropertyDefinitionWrapper<T, T, CX, Any>(index, name, definition).apply {
        addSingle(this)
    }

    /** Add set property [definition] with [name] and [index] */
    fun <T: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: SetDefinition<T, CX>
    ) = SetPropertyDefinitionWrapper<T, CX, Any>(index, name, definition).apply {
        addSingle(this)
    }

    /** Add map property [definition] with [name] and [index] */
    fun <K: Any, V: Any, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: MapDefinition<K, V, CX>
    ) = MapPropertyDefinitionWrapper<K, V, Map<K,V>, CX, Any>(index, name, definition).apply {
        addSingle(this)
    }

    /** Add embedded object property [definition] with [name] and [index] */
    fun <DM: IsValuesDataModel<P>, P: PropertyDefinitions, CX: IsPropertyContext> add(
        index: Int,
        name: String,
        definition: IsEmbeddedValuesDefinition<DM, P, CX>
    ) = EmbeddedValuesPropertyDefinitionWrapper(index, name, definition).apply {
        addSingle(this)
    }

    /** Add a single property definition wrapper */
    fun addSingle(propertyDefinitionWrapper: IsPropertyDefinitionWrapper<out Any, *, *, DO>) {
        @Suppress("UNCHECKED_CAST")
        _allProperties.add(propertyDefinitionWrapper as IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>)

        require(propertyDefinitionWrapper.index in (0..Short.MAX_VALUE)) { "${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} is outside range $(0..Short.MAX_VALUE)" }
        require(indexToDefinition[propertyDefinitionWrapper.index] == null) { "Duplicate index ${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} and ${indexToDefinition[propertyDefinitionWrapper.index]?.name}" }
        indexToDefinition[propertyDefinitionWrapper.index] = propertyDefinitionWrapper
        nameToDefinition[propertyDefinitionWrapper.name] = propertyDefinitionWrapper
    }

    /** Get PropertyReference by [referenceName] */
    final override fun getPropertyReferenceByName(referenceName: String): IsPropertyReference<*, IsPropertyDefinition<*>> {
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
    final override fun getPropertyReferenceByBytes(length: Int, reader: () -> Byte): IsPropertyReference<*, IsPropertyDefinition<*>> {
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
