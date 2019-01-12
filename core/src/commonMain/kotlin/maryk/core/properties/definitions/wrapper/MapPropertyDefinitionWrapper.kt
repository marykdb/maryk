package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.values.Values

/**
 * Contains a Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MapPropertyDefinitionWrapper<K: Any, V: Any, TO: Any, CX: IsPropertyContext, in DO: Any> internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: MapDefinition<K, V, CX>,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, Map<K, V>) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> Map<K, V>?)? = null,
    override val fromSerializable: ((Map<K, V>?) -> TO?)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsMapDefinition<K, V, CX> by definition,
    IsPropertyDefinitionWrapper<Map<K, V>, TO, CX, DO>
{
    override val graphType = PropRefGraphType.PropRef

    @Suppress("UNCHECKED_CAST")
    override fun getRef(parentRef: AnyPropertyReference?): MapReference<K, V, CX> =
        MapReference(this as MapPropertyDefinitionWrapper<K, V, Any, CX, *>, parentRef as CanHaveComplexChildReference<*, *, *, *>?)

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun getKeyRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.definition.getKeyRef(key, this.getRef(parentRef))

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    internal fun getValueRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.definition.getValueRef(key, this.getRef(parentRef))

    /** For quick notation to get a map [key] reference */
    infix fun refToKey(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> MapKeyReference<K, V, *> {
        return { this.getKeyRef(key, it) }
    }

    /** For quick notation to get a map value reference at given [key] */
    infix fun refAt(key: K): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> MapValueReference<K, V, *> {
        return { this.getValueRef(key, it) }
    }
}

/** Specific extension to support fetching sub refs on Map values by [key] */
@Suppress("UNCHECKED_CAST")
fun <K: Any, V: Values<*, P>, DM: IsValuesDataModel<P>, P: PropertyDefinitions, T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, V, *, *, *>.refAtKey(
    key: K,
    propertyDefinitionGetter: P.()-> W
): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<T, W, *> =
    { (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel.ref(this.getValueRef(key, it), propertyDefinitionGetter) }


/** Specific extension to support fetching deeper references on Map values by [key] */
@Suppress("UNCHECKED_CAST")
fun <K: Any, V: Values<*, P>, DM: IsValuesDataModel<P>, P: PropertyDefinitions, T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, V, *, *, *>.at(
    key: K,
    referenceGetter: P.() ->
        (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) ->
            IsPropertyReference<T, W, *>
): (IsPropertyReference<out Any, IsPropertyDefinition<*>, *>?) -> IsPropertyReference<T, W, *> =
    { (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel(this.getValueRef(key, it), referenceGetter) }
