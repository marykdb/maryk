package maryk.core.properties

import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.IsValueDataModelSerializer
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.reflect.KClass

interface IsValueModel<DO: ValueDataObject, DM: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, DM, IsPropertyContext, IsPropertyContext>, IsTypedObjectPropertyDefinitions<DO, DM, IsPropertyContext>, IsNamedObjectModel<DO, DM> {
    override val Serializer: IsValueDataModelSerializer<DO, DM>
    override val Model: ValueDataModelDefinition<DO, DM>

    /** Creates bytes for given [values] */
    fun toBytes(values: ObjectValues<DO, DM>): ByteArray {
        val bytes = ByteArray(this.Model.byteSize)
        var offset = 0

        this.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedStorageBytesEncodable<in Any>
            def.writeStorageBytes(values(index.toUInt() + 1u)) {
                bytes[offset++] = it
            }

            if (offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }
}

abstract class ValueModel<DO: ValueDataObject, P: IsValueModel<DO, *>>(
    objClass: KClass<DO>,
): ObjectModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsValueModel<DO, P> {
    @Suppress("LeakingThis", "UNCHECKED_CAST")
    override val Serializer = object: ValueDataModelSerializer<DO, P>(this as P) {}

    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    fun toBytes(vararg inputs: Any) =
        Serializer.toBytes(*inputs)

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: ValueDataModelDefinition<DO, P>(
        name = objClass.simpleName!!,
        properties = this@ValueModel as P,
    ) {}
}
