package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference

/**
 * Contains a Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MapDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, in DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsMapDefinition<K, V, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, Map<K, V>) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> Map<K, V>?)? = null,
    override val fromSerializable: ((Map<K, V>?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsMapDefinition<K, V, CX> by definition,
    IsMapDefinitionWrapper<K, V, TO, CX, DO> {
    override val graphType = PropRef

    override val anyItemRefCache =
        mutableMapOf<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>()
    override val keyRefCache =
        mutableMapOf<K, MutableMap<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>>()
    override val valueRefCache =
        mutableMapOf<K, MutableMap<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>>()

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?): MapReference<K, V, CX> = cacheRef(parentRef, anyItemRefCache) {
        MapReference(
            this as MapDefinitionWrapper<K, V, Any, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )
    }
}
