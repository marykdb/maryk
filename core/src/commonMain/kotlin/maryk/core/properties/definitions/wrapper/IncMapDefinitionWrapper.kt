package maryk.core.properties.definitions.wrapper

import maryk.core.definitions.MarykPrimitive
import maryk.core.models.BaseDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IncMapAddIndexReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.reflect.KProperty

/**
 * Contains an incrementing Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class IncMapDefinitionWrapper<K : Comparable<K>, V : Any, TO : Any, CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IncrementingMapDefinition<K, V, CX>,
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

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?): IncMapReference<K, V, CX> = cacheRef(parentRef) {
        IncMapReference(
            this as IncMapDefinitionWrapper<K, V, Any, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )
    }

    /** Create a reference which points to an [index] for an add of Incremental Map value */
    fun addIndexRef(index: Int, parentRef: AnyPropertyReference?) =
        IncMapAddIndexReference(
            index = index,
            mapDefinition = this.definition,
            parentReference = parentRef as CanContainMapItemReference<*, *, *>
        )

    // For delegation in definition
    @Suppress("unused")
    operator fun getValue(thisRef: BaseDataModel<DO>, property: KProperty<*>) = this

    override fun validateWithRef(
        previousValue: Map<K, V>?,
        newValue: Map<K, V>?,
        refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K, V>>, *>?
    ) {
        super<IsMapDefinitionWrapper>.validateWithRef(previousValue, newValue, refGetter)
        super<IsMapDefinition>.validateWithRef(previousValue, newValue, refGetter)
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        return super<IsMapDefinitionWrapper>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason) &&
                super<IsMapDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        super<IsMapDefinition>.getAllDependencies(dependencySet)
    }
}
