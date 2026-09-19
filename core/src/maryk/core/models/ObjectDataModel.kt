@file:OptIn(ExperimentalTypeInference::class)

package maryk.core.models

import maryk.core.models.definitions.ObjectDataModelDefinition
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsReferenceCreator
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.dsl.ref as selectReference
import kotlin.experimental.ExperimentalTypeInference
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

/**
 * Object Data Model for defining custom Data Models which can serialize
 * to and from Kotlin objects of type [DO].
 */
abstract class ObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>>(
    objClass: KClass<DO>,
): TypedObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(),
    IsStorableDataModel<DO> {
    /** Select a typed property or embedded path without an extension import. */
    @OverloadResolutionByLambdaReturnType
    @Suppress("UNCHECKED_CAST")
    fun <R : IsPropertyReference<*, *, *>> ref(
        selector: DM.() -> IsReferenceCreator<R>
    ): R = (this as DM).selectReference(selector)

    /** Select a typed property below [parent] without an extension import. */
    @OverloadResolutionByLambdaReturnType
    @Suppress("UNCHECKED_CAST")
    fun <R : IsPropertyReference<*, *, *>> ref(
        parent: AnyOutPropertyReference?,
        selector: DM.() -> IsReferenceCreator<R>
    ): R = (this as DM).selectReference(parent, selector)

    /** Resolve a nested selection below [parent] without an extension import. */
    @JvmName("refFromReferenceGetterWithParent")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> ref(
        parent: AnyOutPropertyReference?,
        selector: DM.() -> (AnyOutPropertyReference?) -> R
    ): R = (this as DM).selectReference(parent, selector)

    /** Resolve a nested selection or a legacy callable-reference selector. */
    @JvmName("refFromReferenceGetter")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> ref(
        selector: DM.() -> (AnyOutPropertyReference?) -> R
    ): R = (this as DM).selectReference(selector)

    override val Meta = ObjectDataModelDefinition(objClass.simpleName!!)
}
