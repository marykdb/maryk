package maryk.core.values

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.inject.AnyInject
import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsFuzzyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.lib.exceptions.ParseException

typealias AnyAbstractValues = AbstractValues<Any, IsDataModel<AbstractPropertyDefinitions<Any>>, AbstractPropertyDefinitions<Any>>

/**
 * Contains a [values] with all values related to a DataObject of [dataModel]
 */
abstract class AbstractValues<DO : Any, DM : IsDataModel<P>, P : AbstractPropertyDefinitions<DO>> : IsValues<P> {
    abstract val dataModel: DM
    internal abstract val values: IsValueItems
    abstract val context: RequestContext?

    /** Retrieve the values size */
    val size get() = values.size

    override fun iterator() = values.iterator()

    /**
     * Utility method to check and values a value to a constructor property
     */
    inline operator fun <reified T> invoke(index: UInt): T {
        val value = this.original(index)

        val valueDef = this.dataModel.properties[index]
            ?: throw DefNotFoundException("Value definition of index $index is missing")

        return process(valueDef, value)
    }

    inline fun <reified T> process(
        valueDef: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        value: Any?
    ): T {
        // Resolve Injects
        val resolvedValue = if (value is AnyInject) {
            value.resolve(this.context ?: throw ContextNotFoundException())
        } else value

        val transformedValue = valueDef.convertToCurrentValue(resolvedValue)

        return when {
            transformedValue is T -> transformedValue
            value is T -> value
            value == Unit -> {
                if (valueDef.required) {
                    throw ParseException("Property '${valueDef.name}' with value '$value' should be non null because is required")
                } else null as T
            }
            else -> throw ParseException(
                "Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as? IsTransportablePropertyDefinitionType<*>)?.propertyDefinitionType?.name
                    ?: "unknown"}"
            )
        }
    }

    /** Get property from values with wrapper in [getProperty] and convert it to native usage */
    inline operator fun <TI : Any, reified TO : Any> invoke(getProperty: P.() -> IsPropertyDefinitionWrapper<TI, TO, *, DO>): TO? {
        val index = getProperty(
            this.dataModel.properties
        ).index

        return invoke(index)
    }

    /** Get property from valuesvalues with wrapper in [getProperty] and convert it to native usage */
    fun <T : Any> original(getProperty: P.() -> IsPropertyDefinitionWrapper<T, *, *, DO>): T? {
        val index = getProperty(
            this.dataModel.properties
        ).index

        @Suppress("UNCHECKED_CAST")
        return this.values[index] as T?
    }

    /** Get ValueItem at [index] from internal list */
    fun getByInternalListIndex(index: Int) =
        (this.values as IsValueItemsImpl).list[index]

    /** Get the original value by [index] */
    fun original(index: UInt) = this.values[index]

    override fun toString(): String {
        val name = if (dataModel is IsNamedDataModel<*>) {
            (dataModel as IsNamedDataModel<*>).name
        } else "ObjectValues"

        return "$name $values"
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any, D : IsPropertyDefinition<T>, C : Any> get(
        propertyReference: IsPropertyReference<T, D, C>
    ): T? {
        val refList = propertyReference.unwrap()
        var value: Any = this
        var fuzzy = false

        for (toResolve in refList) {
            if (fuzzy) {
                // With fuzzy references all resolved results need to be combined into a list
                val list = value as MutableList<Any>
                value = mutableListOf<Any>()
                for (v in list) {
                    val valueToAdd = toResolve.resolve(v) ?: continue
                    if (valueToAdd is List<*>) {
                        value.addAll(valueToAdd as Collection<Any>)
                    } else {
                        value.add(valueToAdd)
                    }
                }
            } else {
                value = toResolve.resolve(value) ?: return null
                fuzzy = fuzzy || toResolve is IsFuzzyReference
            }
        }

        return value as T?
    }

    /** Add to internal values with [index] and [value] */
    internal fun add(index: UInt, value: Any) {
        (this.values as MutableValueItems)[index] = value
    }

    /** Remove from internal valuesvalues by [index] */
    internal fun remove(index: UInt): Any? {
        return (this.values as MutableValueItems).remove(index)?.value
    }
}


/**
 * Transforms the serialized [value] to current value.
 * Returns default value if unset
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any, TO : Any> IsPropertyDefinitionWrapper<T, TO, *, *>.convertToCurrentValue(value: Any?): TO? {
    return when {
        value == null && this.definition is HasDefaultValueDefinition<*> -> (this.definition as? HasDefaultValueDefinition<*>).let {
            it?.default as TO?
        }
        value is ObjectValues<*, *> -> value.toDataObject() as TO?
        else -> try {
            this.fromSerializable?.invoke(value as? T?) ?: value as? TO?
        } catch (e: Throwable) {
            value as? TO?
        }
    }
}
