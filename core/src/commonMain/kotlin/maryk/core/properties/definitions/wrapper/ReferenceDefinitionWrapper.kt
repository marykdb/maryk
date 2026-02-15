package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsRootDataModel
import maryk.core.models.invoke
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsReferenceDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ObjectReferencePropertyReference
import maryk.core.properties.types.Key
import kotlin.reflect.KProperty

/**
 * Contains a reference [definition] of [D].
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO]
 */
data class ReferenceDefinitionWrapper<TO : Any, DM: IsRootDataModel, out D : IsReferenceDefinition<DM, IsPropertyContext>, in DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: D,
    override val alternativeNames: Set<String>? = null,
    override val sensitive: Boolean = false,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((IsPropertyContext, Key<DM>) -> Unit)? = null,
    override val toSerializable: ((TO?, IsPropertyContext?) -> Key<DM>?)? = null,
    override val fromSerializable: ((Key<DM>?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsReferenceDefinition<DM, IsPropertyContext> by definition,
    IsSensitiveValueDefinitionWrapper<Key<DM>, TO, IsPropertyContext, DO>,
    IsFixedStorageBytesEncodable<Key<DM>> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        ObjectReferencePropertyReference(this, parentRef)
    }

    /** For quick notation to fetch property references with [referenceGetter] within embedded object */
    operator fun <T : Any, W : IsPropertyDefinition<T>, R : IsPropertyReference<T, W, *>> invoke(
        referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
    ): (AnyOutPropertyReference?) -> R =
        {
            this.definition.dataModel.invoke(this.ref(it), referenceGetter)
        }

    // For delegation in definition
    @Suppress("unused")
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this

    override fun calculateStorageByteLength(value: Key<DM>): Int {
        return super<IsReferenceDefinition>.calculateStorageByteLength(value)
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        return super<IsReferenceDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason) &&
                super<IsSensitiveValueDefinitionWrapper>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)
    }

    override fun validateWithRef(
        previousValue: Key<DM>?,
        newValue: Key<DM>?,
        refGetter: () -> IsPropertyReference<Key<DM>, IsPropertyDefinition<Key<DM>>, *>?
    ) {
        super<IsSensitiveValueDefinitionWrapper>.validateWithRef(previousValue, newValue, refGetter)
        super<IsReferenceDefinition>.validateWithRef(previousValue, newValue, refGetter)
    }
}
