package maryk.core.properties.definitions.wrapper

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubModelDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SubModelPropertyRef

/** Wrapper for a sub model definition to contain the context on how it relates to DataObject
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param DO: DataObject value type of property for list
 * @param P: Properties object for DataModel
 * @param CX: Context type for property
 * @param DM: Type of DataModel which contains this property
 */
data class SubModelPropertyDefinitionWrapper<DO: Any, P: PropertyDefinitions<DO>, D: DataModel<DO, P, CX>, CX: IsPropertyContext, in DM: Any>(
        override val index: Int,
        override val name: String,
        override val definition: SubModelDefinition<DO, P, D, CX>,
        override val getter: (DM) -> DO?
) :
        IsSubModelDefinition<DO, CX> by definition,
        IsPropertyDefinitionWrapper<DO, CX, DM>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
            SubModelPropertyRef(
                    this,
                    parentRef?.let {
                        it as CanHaveComplexChildReference<*, *, *>
                    }
            )

    /** To get a top level reference on a model
     * @param definitionGetter The fetcher for the property definition to get reference of
     * @return a reference to property
     */
    infix fun ref(definitionGetter: P.() -> IsPropertyDefinitionWrapper<*, *, *>): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<out Any, IsPropertyDefinition<*>> {
        return { definitionGetter(definition.dataModel.properties).getRef(this.getRef(it)) }
    }

    /** For quick notation to fetch property references below submodels
     * @param referenceGetter The sub getter to fetch a reference
     * @return a reference to property
     */
    operator fun invoke(referenceGetter: P.() -> (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<out Any, IsPropertyDefinition<*>>): (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<out Any, IsPropertyDefinition<*>> {
        return { referenceGetter(definition.dataModel.properties)(this.getRef(it)) }
    }
}