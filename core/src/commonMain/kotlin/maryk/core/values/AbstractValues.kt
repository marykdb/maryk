package maryk.core.values

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.inject.AnyInject
import maryk.core.models.AbstractDataModel
import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsFuzzyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
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
        valueDef: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
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

    /** Mutate Values with [pairToAddCreator]. */
    fun mutate(pairToAddCreator: P.() -> Array<ValueItem>) {
        val mutatableValues = values as MutableValueItems

        for (toAdd in pairToAddCreator(this.dataModel.properties)) {
            mutatableValues += toAdd
        }
    }

    /** Get property from values with wrapper in [getProperty] and convert it to native usage */
    inline operator fun <TI : Any, reified TO : Any> invoke(getProperty: P.() -> IsDefinitionWrapper<TI, TO, *, DO>): TO? {
        val index = getProperty(
            this.dataModel.properties
        ).index

        return invoke(index)
    }

    /** Get property from valuesvalues with wrapper in [getProperty] and convert it to native usage */
    fun <T : Any> original(getProperty: P.() -> IsDefinitionWrapper<T, *, *, DO>): T? {
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

    /**
     * Method to walk to any value and process it with the reference
     * Pass [parentReference] to process within context of parent
     */
    fun processAllValues(parentReference: IsPropertyReference<*, *, *>? = null, processor: (IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>, Any) -> Unit) {
        for ((index, value) in this.values) {
            this.dataModel.properties[index]?.let { definition ->
                processDefinitionForProcessor(
                    definition.definition as IsPropertyDefinition<out Any>,
                    value,
                    definition.ref(parentReference),
                    processor
                )
            }
        }
    }

    private fun processDefinitionForProcessor(
        definition: IsPropertyDefinition<out Any>,
        value: Any,
        parentReference: IsPropertyReference<*, *, *>?,
        processor: (IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>, Any) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        when (definition) {
            is IsEmbeddedValuesDefinition<*, *, *> -> {
                (value as AbstractValues<*, *, *>).processAllValues(parentReference, processor)
            }
            is IsListDefinition<*, *> ->
                for ((listIndex, item) in (value as List<Any>).withIndex()) {
                    val itemRef = definition.itemRef(
                        listIndex.toUInt(),
                        parentReference as CanContainListItemReference<*, *, *>
                    ) as IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>
                    processor(itemRef, item)
                }
            is IsSetDefinition<*, *> -> {
                for (item in value as Set<Any>) {
                    val itemRef = (definition as IsSetDefinition<Any, *>).itemRef(
                        item,
                        parentReference as CanContainSetItemReference<*, *, *>
                    )
                    processor(itemRef as IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>, item)
                }
            }
            is IsMapDefinition<out Any, out Any, *> -> {
                for ((key, item) in value as Map<Any, Any>) {
                    val itemRef = (definition as IsMapDefinition<Any, out Any, *>).valueRef(
                        key,
                        parentReference as CanContainMapItemReference<*, *, *>
                    ) as IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>

                    processDefinitionForProcessor(
                        definition.valueDefinition,
                        item,
                        itemRef,
                        processor
                    )
                }
            }
            is IsMultiTypeDefinition<out TypeEnum<Any>, out Any, *> -> {
                val multiType = value as TypedValue<TypeEnum<Any>, Any>
                val typeReference = (definition as IsMultiTypeDefinition<TypeEnum<Any>, Any, *>).typedValueRef(
                    multiType.type,
                    parentReference as CanHaveComplexChildReference<*, *, *, *>
                )
                processDefinitionForProcessor(
                    definition.definition(multiType.type) as IsPropertyDefinition<out Any>,
                    multiType.value,
                    typeReference,
                    processor
                )
            }
            else ->
                processor(
                    parentReference as IsPropertyReference<out Any, IsPropertyDefinition<out Any>, IsValues<*>>,
                    value
                )
        }
    }

}


/**
 * Transforms the serialized [value] to current value.
 * Returns default value if unset
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any, TO : Any> IsDefinitionWrapper<T, TO, *, *>.convertToCurrentValue(value: Any?): TO? {
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

/** Output values to a json string with possible [context] provided */
fun <V: AbstractValues<DO, DM, P>, DO: Any, DM: AbstractDataModel<DO, P, V, *, CX>, P: AbstractPropertyDefinitions<DO>, CX: IsPropertyContext> V.toJson(
    context: CX? = null,
    pretty: Boolean = false
): String =
    this.dataModel.writeJson(this, context = context, pretty = pretty)
