package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainListItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

/** Defines a List definition */
interface IsListDefinition<T : Any, CX : IsPropertyContext> :
    IsCollectionDefinition<T, List<T>, CX, IsSubDefinition<T, CX>>,
    IsChangeableValueDefinition<List<T>, CX>,
    HasDefaultValueDefinition<List<T>> {
    /** Get a reference to a specific list item on [parentList] by [index]. */
    fun itemRef(index: UInt, parentList: CanContainListItemReference<*, *, *>?) =
        ListItemReference(index, this, parentList)

    /** Get a reference to any list item on [parentList]. */
    fun anyItemRef(parentList: IsPropertyReference<*, *, *>?) =
        ListAnyItemReference(this, parentList)

    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    override fun getItemPropertyRefCreator(index: UInt, item: T) =
        { parentRef: AnyPropertyReference? ->
            @Suppress("UNCHECKED_CAST")
            this.itemRef(index, parentRef as ListReference<T, CX>?) as IsPropertyReference<Any, *, *>
        }

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>, *>?,
        newValue: List<T>,
        validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    ) {
        newValue.forEachIndexed { index, item ->
            validator(item) {
                @Suppress("UNCHECKED_CAST")
                this.itemRef(index.toUInt(), refGetter() as ListReference<T, CX>?)
            }
        }
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?,
    ): Boolean {
        var compatible = super<IsCollectionDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        if (definition is IsListDefinition<*, *>) {
            compatible = isCompatible(definition, addIncompatibilityReason) && compatible

            compatible = valueDefinition.compatibleWith(definition.valueDefinition, checkedDataModelNames) { addIncompatibilityReason?.invoke("Value: $it") } && compatible
        }

        return compatible
    }
}
