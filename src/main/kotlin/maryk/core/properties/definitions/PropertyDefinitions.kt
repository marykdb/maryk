package maryk.core.properties.definitions

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.DataObjectFixedBytesProperty
import maryk.core.properties.definitions.wrapper.DataObjectListProperty
import maryk.core.properties.definitions.wrapper.DataObjectMapProperty
import maryk.core.properties.definitions.wrapper.DataObjectProperty
import maryk.core.properties.definitions.wrapper.DataObjectSetProperty
import maryk.core.properties.definitions.wrapper.DataObjectSubModelProperty
import maryk.core.properties.definitions.wrapper.IsDataObjectProperty

abstract class PropertyDefinitions<DM: Any> {
    @Suppress("PropertyName")
    internal val __allProperties = mutableListOf<IsDataObjectProperty<*, *, DM>>()

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFlexBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DM) -> T?
    ) = DataObjectProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext, D: IsSerializableFixedBytesEncodable<T, CX>> add(
            index: Int,
            name: String,
            definition: D,
            getter: (DM) -> T?
    ) = DataObjectFixedBytesProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: ListDefinition<T, CX>,
            getter: (DM) -> List<T>?
    ) = DataObjectListProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <T: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SetDefinition<T, CX>,
            getter: (DM) -> Set<T>?
    ) = DataObjectSetProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    protected fun <K: Any, V: Any, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: MapDefinition<K, V, CX>,
            getter: (DM) -> Map<K, V>?
    ) = DataObjectMapProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }

    fun <DO: Any, D: DataModel<DO, CX>, CX: IsPropertyContext> add(
            index: Int,
            name: String,
            definition: SubModelDefinition<DO, D, CX>,
            getter: (DM) -> DO?
    ) = DataObjectSubModelProperty(index, name, definition, getter).apply {
        __allProperties.add(this)
    }
}