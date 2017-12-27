package maryk.core.properties.definitions

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper
import maryk.core.properties.types.TypedValue

/** A collection of Property Definitions which can be used to model a DataModel */
abstract class PropertyDefinitions<DO: Any> : Iterable<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>> {
    override fun iterator() = _allProperties.iterator()

    private val _allProperties = mutableListOf<IsPropertyDefinitionWrapper<Any, IsPropertyContext, DO>>()

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

    /** Add a single property definition wrapper */
    internal fun add(propertyDefinitionWrapper: IsPropertyDefinitionWrapper<*, *, DO>) {
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
}