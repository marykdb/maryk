package maryk.core.objects

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.AnyInject
import maryk.core.query.DefinitionsContext
import maryk.lib.exceptions.ParseException

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
abstract class AbstractValues<DO: Any, DM: IsDataModel<P>, P: AbstractPropertyDefinitions<DO>> {
    abstract val dataModel: DM
    protected abstract val map: Map<Int, Any?>
    abstract val context: DefinitionsContext?

    /** Retrieve the keys of the map */
    val keys get() = map.keys

    /** Retrieve the map size */
    val size get() = map.size

    /**
     * Utility method to check and map a value to a constructor property
     */
    inline operator fun <reified T> invoke(index: Int): T {
        val value = this.original(index)

        val valueDef = this.dataModel.properties[index]
                ?: throw Exception("Value definition of index $index is missing")

        // Resolve Injects
        val resolvedValue = if (value is AnyInject) {
            value.resolve(this.context ?: throw ContextNotFoundException())
        } else value

        val transformedValue = valueDef.convertToCurrentValue(resolvedValue)

        return when {
            transformedValue is T -> transformedValue
            value is T -> value
            else -> throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
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
        val name = if (dataModel is IsNamedDataModel<*>) {
            (dataModel as IsNamedDataModel<*>).name
        } else "ObjectValues"

        return "$name $map"
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any, D: IsPropertyDefinition<T>, C: Any> get(propertyReference: IsPropertyReference<T, D, C>): T? {
        val refList = mutableListOf(propertyReference as IsPropertyReference<*, *, in Any>)

        var ref: IsPropertyReference<*, *, in Any> = propertyReference
        while (ref is PropertyReference<*, *, *, *> && ref.parentReference != null) {
            ref = ref.parentReference as IsPropertyReference<*, *, in Any>
            refList.add(0, ref)
        }

        var value: Any = this
        for (toResolve in refList) {
            value = toResolve.resolve(value)
                    ?: return null
        }

        return value as T?
    }
}
