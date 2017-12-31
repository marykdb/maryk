package maryk.core.properties.definitions

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32

data class SetDefinition<T: Any, CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        override val valueDefinition: IsValueDefinition<T, CX>
) : IsCollectionDefinition<T, Set<T>, CX, IsValueDefinition<T, CX>> {
    override val propertyDefinitionType = PropertyDefinitionType.Set

    init {
        require(valueDefinition.required, { "Definition for value should have required=true on set" })
    }

    override fun newMutableCollection(context: CX?) = mutableSetOf<T>()

    /** Get a reference to a specific set item
     * @param value to get reference for
     * @param setReference (optional) factory to create parent ref
     */
    fun getItemRef(value: T, setReference: SetReference<T, CX>?)
            = SetItemReference(value, this, setReference)

    override fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<Set<T>, IsPropertyDefinition<Set<T>>>?, newValue: Set<T>, validator: (item: T, parentRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any) {
        newValue.forEach {
            validator(it) {
                @Suppress("UNCHECKED_CAST")
                this.getItemRef(it, refGetter() as SetReference<T, CX>?)
            }
        }
    }

    object Model : SimpleDataModel<SetDefinition<*, *>, PropertyDefinitions<SetDefinition<*, *>>>(
            properties = object : PropertyDefinitions<SetDefinition<*, *>>() {
                init {
                    IsPropertyDefinition.addIndexed(this, SetDefinition<*, *>::indexed)
                    IsPropertyDefinition.addSearchable(this, SetDefinition<*, *>::searchable)
                    IsPropertyDefinition.addRequired(this, SetDefinition<*, *>::required)
                    IsPropertyDefinition.addFinal(this, SetDefinition<*, *>::final)
                    HasSizeDefinition.addMinSize(4, this) { it.minSize?.toUInt32() }
                    HasSizeDefinition.addMaxSize(5, this) { it.maxSize?.toUInt32() }
                    add(6, "valueDefinition", MultiTypeDefinition(
                            definitionMap = mapOfPropertyDefSubModelDefinitions
                    )) {
                        val defType = it.valueDefinition as IsTransportablePropertyDefinitionType
                        TypedValue(defType.propertyDefinitionType, it.valueDefinition)
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = SetDefinition(
                indexed = map[0] as Boolean,
                searchable = map[1] as Boolean,
                required = map[2] as Boolean,
                final = map[3] as Boolean,
                minSize = (map[4] as UInt32?)?.toInt(),
                maxSize = (map[5] as UInt32?)?.toInt(),
                valueDefinition = (map[6] as TypedValue<PropertyDefinitionType, IsValueDefinition<*, *>>).value
        )
    }
}