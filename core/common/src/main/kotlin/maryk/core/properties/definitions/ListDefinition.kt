package maryk.core.properties.definitions

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.TypedValue

/** Definition for List property */
data class ListDefinition<T: Any, CX: IsPropertyContext>(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    override val valueDefinition: IsValueDefinition<T, CX>
) : IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>> {
    override val propertyDefinitionType = PropertyDefinitionType.List

    init {
        require(valueDefinition.required, { "Definition for value should have required=true on List" })
    }

    override fun newMutableCollection(context: CX?) = mutableListOf<T>()

    /** Get a reference to a specific list item on [parentList] by [index]. */
    fun getItemRef(index: Int, parentList: ListReference<T, CX>?) =
        ListItemReference(index, this, parentList)

    override fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<List<T>, IsPropertyDefinition<List<T>>>?, newValue: List<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any) {
        newValue.forEachIndexed { index, item ->
            validator(item) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(index, refGetter() as ListReference<T, CX>?)
            }
        }
    }

    object Model : SimpleDataModel<ListDefinition<*, *>, PropertyDefinitions<ListDefinition<*, *>>>(
        properties = object : PropertyDefinitions<ListDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, ListDefinition<*, *>::indexed)
                IsPropertyDefinition.addSearchable(this, ListDefinition<*, *>::searchable)
                IsPropertyDefinition.addRequired(this, ListDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, ListDefinition<*, *>::final)
                HasSizeDefinition.addMinSize(4, this, ListDefinition<*, *>::minSize)
                HasSizeDefinition.addMaxSize(5, this, ListDefinition<*, *>::maxSize)
                add(6, "valueDefinition",
                    MultiTypeDefinition(
                        definitionMap = mapOfPropertyDefSubModelDefinitions
                    ),
                    getter = ListDefinition<*, *>::valueDefinition,
                    toSerializable = {
                        val defType = it!! as IsTransportablePropertyDefinitionType<*>
                        TypedValue(defType.propertyDefinitionType, it)
                    },
                    fromSerializable = { it ->
                        it?.value as IsValueDefinition<*, *>
                    }
                )
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = ListDefinition(
            indexed = map(0, false),
            searchable = map(1, true),
            required = map(2, true),
            final = map(3, false),
            minSize = map(4),
            maxSize = map(5),
            valueDefinition = map<IsValueDefinition<*, *>>(6)
        )
    }
}
