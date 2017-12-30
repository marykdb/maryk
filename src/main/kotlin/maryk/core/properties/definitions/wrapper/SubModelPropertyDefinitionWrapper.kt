package maryk.core.properties.definitions.wrapper

import maryk.core.objects.AbstractDataModel
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubModelDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SubModelPropertyRef
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32

/** Wrapper for a sub model definition to contain the context on how it relates to DataObject
 * @param index: of definition to encode into ProtoBuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param SDO: DataObject value type of property for sub object
 * @param P: Properties object for DataModel
 * @param DM: type of DataModel which describes the DataObject
 * @param CXI: Input Context type for property
 * @param CX: Context type for property
 * @param DO: Type of DataObject which contains this property
 */
data class SubModelPropertyDefinitionWrapper<SDO: Any, out P: PropertyDefinitions<SDO>, out DM: AbstractDataModel<SDO, P, CXI, CX>, CXI: IsPropertyContext, CX: IsPropertyContext, in DO: Any>(
        override val index: Int,
        override val name: String,
        override val definition: SubModelDefinition<SDO, P, DM, CXI, CX>,
        override val getter: (DO) -> SDO?
) :
        IsSubModelDefinition<SDO, CXI> by definition,
        IsPropertyDefinitionWrapper<SDO, CXI, DO>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
            SubModelPropertyRef(
                    this,
                    parentRef?.let {
                        it as CanHaveComplexChildReference<*, *, *>
                    }
            )

    /** To get a top level reference on a model
     * @param propertyDefinitionGetter The fetcher for the property definition to get reference of
     * @return a reference to property
     */
    infix fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *>> ref(
            propertyDefinitionGetter: P.()-> W
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> {
        return { this.definition.dataModel.ref(this.getRef(it), propertyDefinitionGetter) }
    }

    /** For quick notation to fetch property references below sub models
     * @param referenceGetter The sub getter to fetch a reference
     * @return a reference to property
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
            referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) ->
            IsPropertyReference<T, W>
    ): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W> {
        return { this.definition.dataModel(this.getRef(it), referenceGetter) }
    }

    companion object : SimpleDataModel<SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>, PropertyDefinitions<SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>>>(
            properties = object : PropertyDefinitions<SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>>() {
                init {
                    IsPropertyDefinitionWrapper.addIndex(this, SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>::index)
                    IsPropertyDefinitionWrapper.addName(this, SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>::name)
                    IsPropertyDefinitionWrapper.addDefinition(this, SubModelPropertyDefinitionWrapper<*, *, *, *, *, *>::definition)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = SubModelPropertyDefinitionWrapper(
                index = (map[0] as UInt32).toInt(),
                name = map[1] as String,
                definition = (map[2] as TypedValue<PropertyDefinitionType, SubModelDefinition<Any, PropertyDefinitions<Any>, AbstractDataModel<Any, PropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>>).value,
                getter = { _: Any -> null }
        )
    }
}