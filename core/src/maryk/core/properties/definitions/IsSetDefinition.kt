package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainSetItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference

/** Defines a set definition */
interface IsSetDefinition<T : Any, CX : IsPropertyContext> :
    IsCollectionDefinition<T, Set<T>, CX, IsValueDefinition<T, CX>>,
    HasDefaultValueDefinition<Set<T>> {
    /** Get a reference by [value] to a specific set item of set of [setReference] */
    fun itemRef(value: T, setReference: CanContainSetItemReference<*, *, *>?) =
        SetItemReference(value, this, setReference)

    /** Get a reference to any set item in [setReference] */
    fun anyItemRef(setReference: SetReference<T, CX>?) =
        SetAnyValueReference(this, setReference)

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    override fun getItemPropertyRefCreator(
        index: UInt,
        item: T
    ) = { parentRef: AnyPropertyReference? ->
        @Suppress("UNCHECKED_CAST")
        this.itemRef(item, parentRef as SetReference<T, CX>?) as IsPropertyReference<Any, *, *>
    }

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>, *>?,
        newValue: Set<T>,
        validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    ) {
        for (it in newValue) {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.itemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        if (definition is IsSetDefinition<*, *>) {
            compatible = isCompatible(definition, addIncompatibilityReason) && compatible

            compatible = valueDefinition.compatibleWith(definition.valueDefinition, checkedDataModelNames) { addIncompatibilityReason?.invoke("Value: $it") } && compatible
        }

        return compatible
    }
}
