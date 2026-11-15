package maryk.datastore.shared

import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference

/** Scalar fields include stored object references; narrow planners can require stricter proof. */
fun simpleScalarReadProperty(node: IsPropRefGraphNode<*>): IsDefinitionWrapper<*, *, *, *>? =
    (node as? IsDefinitionWrapper<*, *, *, *>)?.takeIf { it.definition is IsSimpleValueDefinition<*, *> }

/** Prove canonical scalar dependencies without making any backend read or metadata decisions. */
fun IsRootDataModel.rootScalarReadProperty(reference: AnyPropertyReference): IsDefinitionWrapper<*, *, *, *>? {
    if (reference::class != ValueWithFixedBytesPropertyReference::class &&
        reference::class != ValueWithFlexBytesPropertyReference::class) return null
    if ((reference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference != null) return null
    val property = reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *> ?: return null
    return property.takeIf { it.definition is IsSimpleValueDefinition<*, *> && this[it.index] === it }
}

fun <DM : IsRootDataModel> DM.scalarReadReference(node: IsPropRefGraphNode<DM>): AnyPropertyReference? {
    val property = simpleScalarReadProperty(node) ?: return null
    val reference = property.ref()
    return reference.takeIf { rootScalarReadProperty(it) != null }
}
