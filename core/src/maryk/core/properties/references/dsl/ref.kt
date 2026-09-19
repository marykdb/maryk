@file:OptIn(ExperimentalTypeInference::class)

package maryk.core.properties.references.dsl

import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsReferenceCreator
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.experimental.ExperimentalTypeInference
import kotlin.jvm.JvmName

/** Select a property while retaining its model receiver and value type. */
@OverloadResolutionByLambdaReturnType
fun <DM : IsDataModel, R : IsPropertyReference<*, *, *>> DM.ref(
    selector: DM.() -> IsReferenceCreator<R>
): R = selector(this).ref(null)

/** Select a property below [parent] while retaining its model receiver and value type. */
@OverloadResolutionByLambdaReturnType
fun <DM : IsDataModel, R : IsPropertyReference<*, *, *>> DM.ref(
    parent: AnyOutPropertyReference?,
    selector: DM.() -> IsReferenceCreator<R>
): R = selector(this).ref(parent)

/** Resolve a nested selection below [parent]. */
@JvmName("refFromReferenceGetterWithParent")
fun <DM : IsDataModel, T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> DM.ref(
    parent: AnyOutPropertyReference?,
    selector: DM.() -> (AnyOutPropertyReference?) -> R
): R = selector(this)(parent)

/** Resolve a nested selection, including existing callable-reference selectors. */
@JvmName("refFromReferenceGetter")
fun <DM : IsDataModel, T : Any, D : IsPropertyDefinition<T>, R : IsPropertyReference<T, D, *>> DM.ref(
    selector: DM.() -> (AnyOutPropertyReference?) -> R
): R = selector(this)(null)
