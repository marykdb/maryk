package maryk.core.properties.definitions

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MapPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SetPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.SubModelPropertyDefinitionWrapper

abstract class PropertyDefinitions<DO: Any> {
    @Suppress("PropertyName")
    internal val __allProperties = mutableListOf<IsPropertyDefinitionWrapper<*, *, DO>>()

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DO) -> T? = { null }
    ) = PropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DO) -> T? = { null }
    ) = FixedBytesPropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: ListDefinition<T, CX>,
            getter: (DO) -> List<T>? = { null }
    ) = ListPropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SetDefinition<T, CX>,
            getter: (DO) -> Set<T>? = { null }
    ) = SetPropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    protected fun <K: Any, V: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: MapDefinition<K, V, CX>,
            getter: (DO) -> Map<K, V>? = { null }
    ) = MapPropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <SDO: Any, P: PropertyDefinitions<SDO>, D: DataModel<SDO, P, CX>, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SubModelDefinition<SDO, P, D, CX>,
            getter: (DO) -> SDO? = { null }
    ) = SubModelPropertyDefinitionWrapper(index, name, definition, getter).apply {
        __allProperties.add(this)
    }
}