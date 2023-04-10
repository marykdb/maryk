package maryk.core.models

import maryk.core.models.serializers.SingleValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.values.ObjectValues

typealias SingleTypedValueDataModel<T, DO, DM, CX> = SingleValueDataModel<T, T, DO, DM, CX>

abstract class SingleValueDataModel<T : Any, TO : Any, DO : Any, DM : IsObjectDataModel<DO>, CX : IsPropertyContext>(
    val singlePropertyDefinitionGetter: () -> IsDefinitionWrapper<T, out TO, CX, DO>,
): InternalObjectDataModel<DO, DM, CX, CX>() {
    /** Creates a Data Object by [values] */
    abstract override fun invoke(values: ObjectValues<DO, DM>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object : SingleValueDataModelSerializer<T, TO, DO, DM, CX>(
        model = this as DM,
        singlePropertyDefinitionGetter = singlePropertyDefinitionGetter,
    ) {}
}
