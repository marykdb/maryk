package maryk.core.objects

import maryk.core.models.DataModel
import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.lib.exceptions.ParseException

typealias SimpleValues<DO> = Values<DO, ObjectPropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class Values<DO: Any, P: ObjectPropertyDefinitions<DO>> internal constructor(
    val dataModel: IsDataModel<DO, P>,
    private val map: Map<Int, Any?>
) {
    /**
     * Converts map to a strong typed DataObject.
     * Will throw exception if map is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this)

    /** Retrieve the keys of the map */
    val keys get() = map.keys

    /** Retrieve the map size */
    val size get() = map.size

    /**
     * Utility method to check and map a value to a constructor property
     */
    inline operator fun <reified T> invoke(index: Int): T {
        val value = this.original(index)

        val valueDef = this.dataModel.properties.getDefinition(index)
                ?: throw Exception("Value definition of index $index is missing")

        val transformedValue = valueDef.convertToCurrentValue(value)

        return if (transformedValue is T) {
            transformedValue
        } else if (value is T) {
            value
        } else {
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }
    }

    /** Get property from map with wrapper in [getProperty] and convert it to native usage */
    inline operator fun <TI: Any, reified TO: Any> invoke(getProperty: P.() -> IsPropertyDefinitionWrapper<TI, TO, *, DO>): TO? {
        val index = getProperty(
            this.dataModel.properties
        ).index

        return invoke(index)
    }

    /** Get property from map with wrapper in [getProperty] and convert it to native usage */
    fun <T: Any> original(getProperty: P.() -> IsPropertyDefinitionWrapper<T, *, *, DO>): T? {
        val index = getProperty(
            this.dataModel.properties
        ).index

        @Suppress("UNCHECKED_CAST")
        return this.map[index] as T?
    }

    /** Get the original value by [index] */
    fun original(index: Int) = this.map[index]

    override fun toString(): String {
        val name = if (dataModel is DataModel<*, *>) {
            dataModel.name
        } else "Values"

        return "$name $map"
    }
}
