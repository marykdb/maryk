package maryk.core.properties

import maryk.core.models.serializers.IsObjectDataModelSerializer
import maryk.core.models.serializers.SingleValueDataModelSerializer
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.values.ObjectValues

interface IsSingleValueModel<T : Any, TO : Any, DO : Any, P : IsObjectPropertyDefinitions<DO>, CX : IsPropertyContext>: IsBaseModel<DO, P, CX, CX>, IsTypedObjectPropertyDefinitions<DO, P, CX> {
    override val Serializer: IsObjectDataModelSerializer<DO, P, CX, CX>
}

typealias SingleTypedValueModel<T, DO, P, CX> = SingleValueModel<T, T, DO, P, CX>

abstract class SingleValueModel<T : Any, TO : Any, DO : Any, P : IsObjectPropertyDefinitions<DO>, CX : IsPropertyContext>(
    val singlePropertyDefinitionGetter: () -> IsDefinitionWrapper<T, out TO, CX, DO>,
): ObjectModel<DO, P, CX, CX>(), IsSingleValueModel<T, TO, DO, P, CX> {
    /** Creates a Data Object by [values] */
    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object : SingleValueDataModelSerializer<T, TO, DO, P, CX>(
        model = this as P,
        singlePropertyDefinitionGetter = singlePropertyDefinitionGetter,
    ) {}
}
