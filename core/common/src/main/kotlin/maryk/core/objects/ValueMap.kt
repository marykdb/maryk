package maryk.core.objects

import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.lib.exceptions.ParseException

typealias SimpleValueMap<DO> = ValueMap<DO, PropertyDefinitions<DO>>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class ValueMap<DO: Any, P: PropertyDefinitions<DO>> internal constructor(
    val dataModel: IsDataModel<DO, P>,
    val map: Map<Int, Any?>
) : Map<Int, Any?> by map {
    /**
     * Converts map to a strong typed DataObject.
     * Will throw exception if map is missing values for a complete DataObject
     */
    fun toDataObject() = this.dataModel.invoke(this)

    /**
     * Utility method to check and map a value to a constructor property
     */
    inline operator fun <reified T> invoke(index: Int): T {
        val value = this[index]

        val valueDef = this.dataModel.properties.getDefinition(index)
                ?: throw Exception("Value definition of index $index is missing")

        val valueDefDefinition = valueDef.definition

        if (value == null && valueDefDefinition is HasDefaultValueDefinition<*>) {
            return valueDefDefinition.default as T
        }

        val transformedValue = valueDef.fromSerializable?.invoke(value) ?: value

        return if (transformedValue is T) {
            transformedValue
        } else if (value != null && transformedValue == null) {
            value as T
        } else {
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }
    }

    /** Get property from map with wrapper in [getProperty] and convert it to native usage */
    inline operator fun <TI: Any, reified TO: Any> invoke(getProperty: P.() -> IsPropertyDefinitionWrapper<TI, TO, *, DO>): TO? =
        invoke(
            getProperty(
                this.dataModel.properties
            ).index
        )

    /** Get property from map with wrapper in [getProperty] and convert it to native usage */
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> original(getProperty: P.() -> IsPropertyDefinitionWrapper<T, *, *, DO>): T? =
        this[
            getProperty(
                this.dataModel.properties
            ).index
        ] as T?
}
