package maryk.core.models

import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.reflect.KClass

abstract class ValueDataModel<DO: ValueDataObject, DM: IsValueDataModel<DO, *>>(
    objClass: KClass<DO>,
): ObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(), IsValueDataModel<DO, DM> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    private val typedThis: DM = this as DM

    override val Serializer = object: ValueDataModelSerializer<DO, DM>(typedThis) {}

    abstract override fun invoke(values: ObjectValues<DO, DM>): DO

    fun toBytes(vararg inputs: Any) =
        Serializer.toBytes(*inputs)

    override val Model = object: ValueDataModelDefinition<DO, DM>(
        name = objClass.simpleName!!,
        properties = typedThis,
    ) {}
}
