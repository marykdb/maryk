package maryk.core.properties.references.dsl

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

/** Specific extension to support fetching deeper references on Map values by any key */
fun <K : Any, V : Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> IsSubDefinition<Map<K, V>, *>.any(
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val mapDefinition = this as IsMapDefinition<K, V, *>

        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        (mapDefinition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel(
            this.anyValueRef(parent as CanContainMapItemReference<*, *, *>),
            referenceGetter
        )
    }
