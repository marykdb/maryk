package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.objects.AbstractDataModel
import maryk.core.objects.DefinitionDataModel
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
import maryk.core.properties.types.TypedValue

/** A collection of Property Definitions which can be used to model a DataModel */
abstract class PropertyDefinitions<DO: Any>(
        properties: MutableList<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> = mutableListOf()
) : Iterable<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> {
    override fun iterator() = _allProperties.iterator()

    private val _allProperties: MutableList<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> = mutableListOf()

    private val indexToDefinition = mutableMapOf<Int, IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>()
    private val nameToDefinition = mutableMapOf<String, IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>()

    /** Get the definition with a property [name] */
    fun getDefinition(name: String) = nameToDefinition[name]
    /** Get the definition with a property [index] */
    fun getDefinition(index: Int) = indexToDefinition[index]

    /** Get a method to retrieve property from DataObject by [name] */
    fun getPropertyGetter(name: String) = nameToDefinition[name]?.getter
    /** Get a method to retrieve property from DataObject by [index] */
    fun getPropertyGetter(index: Int) = indexToDefinition[index]?.getter

    init {
        properties.forEach { add(it) }
    }

    /** Add a single property definition wrapper */
    private fun add(propertyDefinitionWrapper: IsPropertyDefinitionWrapper<out Any, *, DO>) {
        @Suppress("UNCHECKED_CAST")
        _allProperties.add(propertyDefinitionWrapper as IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>)

        require(propertyDefinitionWrapper.index in (0..Short.MAX_VALUE), { "${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} is outside range $(0..Short.MAX_VALUE)" })
        require(indexToDefinition[propertyDefinitionWrapper.index] == null, { "Duplicate index ${propertyDefinitionWrapper.index} for ${propertyDefinitionWrapper.name} and ${indexToDefinition[propertyDefinitionWrapper.index]?.name}" })
        indexToDefinition[propertyDefinitionWrapper.index] = propertyDefinitionWrapper
        nameToDefinition[propertyDefinitionWrapper.name] = propertyDefinitionWrapper
    }

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DO) -> T? = { null }
    ) = PropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DO) -> T? = { null }
    ) = FixedBytesPropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    fun <T: Any> add(
            index: Int,
            name: String,
            definition: ListDefinition<T, *>,
            getter: (DO) -> List<T>? = { null }
    ) = ListPropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    fun <T: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SetDefinition<T, CX>,
            getter: (DO) -> Set<T>? = { null }
    ) = SetPropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    protected fun <K: Any, V: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: MapDefinition<K, V, CX>,
            getter: (DO) -> Map<K, V>? = { null }
    ) = MapPropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    protected fun <CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: MultiTypeDefinition<CX>,
            getter: (DO) -> TypedValue<*>? = { null }
    ) = PropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    fun <SDO: Any, P: PropertyDefinitions<SDO>, D: AbstractDataModel<SDO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SubModelDefinition<SDO, P, D, CXI, CX>,
            getter: (DO) -> SDO? = { null }
    ) = SubModelPropertyDefinitionWrapper(index, name, definition, getter).apply {
        add(this)
    }

    /** Get PropertyReference by name
     * @param referenceName to parse for a property reference
     */
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

        return propertyReference!!
    }

    /** Get PropertyReference by bytes
     * @param length of bytes to read
     * @param reader to read for a property reference
     */
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

        return propertyReference!!
    }


    object Model : DefinitionDataModel<PropertyDefinitions<out Any>>(
            properties = object : PropertyDefinitions<PropertyDefinitions<out Any>>() {
                init {
                    add(0, "properties", ListDefinition(
                            valueDefinition = MultiTypeDefinition(
                                    definitionMap = mapOfPropertyDefWrapperDefinitions
                            )
                    )) { propertyDefinitions ->
                        propertyDefinitions.map {
                            val def = it.definition as IsTransportablePropertyDefinitionType
                            TypedValue(def.propertyDefinitionType.index, it)
                        }
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = object : PropertyDefinitions<PropertyDefinitions<Any>>(
                properties =
                    (map[0] as List<TypedValue<IsPropertyDefinitionWrapper<Any, IsPropertyContext, Any>>>).map {
                        it.value
                    }.toMutableList()
        ){}
    }
}