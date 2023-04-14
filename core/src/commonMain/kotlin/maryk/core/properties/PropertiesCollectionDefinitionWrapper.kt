package maryk.core.properties

import maryk.core.models.IsTypedDataModel
import maryk.core.models.IsTypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.wrapper.AnyTypedDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.DefinitionsConversionContext

/**
 * Wrapper specifically to wrap a PropertiesCollectionDefinition
 */
data class PropertiesCollectionDefinitionWrapper<DO : Any>(
    override val index: UInt,
    override val name: String,
    override val definition: PropertiesCollectionDefinition<DO>,
    override val getter: (DO) -> IsTypedDataModel<DO>?,
    override val alternativeNames: Set<String>? = null
) :
    IsCollectionDefinition<AnyTypedDefinitionWrapper<DO>, IsTypedDataModel<DO>, DefinitionsConversionContext, EmbeddedObjectDefinition<AnyTypedDefinitionWrapper<DO>, IsTypedObjectDataModel<AnyTypedDefinitionWrapper<DO>, *, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>> by definition,
    IsDefinitionWrapper<IsTypedDataModel<DO>, IsTypedDataModel<DO>, DefinitionsConversionContext, DO>
{
    override val graphType = PropRefGraphType.PropRef

    override val toSerializable: (Unit.(IsTypedDataModel<DO>?, DefinitionsConversionContext?) -> IsTypedDataModel<DO>?)? = null
    override val fromSerializable: (Unit.(IsTypedDataModel<DO>?) -> IsTypedDataModel<DO>?)? = null
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
    override val capturer: (Unit.(DefinitionsConversionContext, IsTypedDataModel<DO>) -> Unit)? = null

    override fun ref(parentRef: AnyPropertyReference?) = throw NotImplementedError()
}
