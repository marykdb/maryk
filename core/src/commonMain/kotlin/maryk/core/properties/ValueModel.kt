package maryk.core.properties

import maryk.core.models.ValueDataModel
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.reflect.KClass

interface IsValueModel<DO: ValueDataObject, P: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, P, IsPropertyContext, IsPropertyContext>, IsTypedObjectPropertyDefinitions<DO, P, IsPropertyContext>, IsObjectModel<DO, P> {
    override val Model: ValueDataModel<DO, P>

    /** Creates bytes for given [values] */
    fun toBytes(values: ObjectValues<DO, P>): ByteArray {
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

abstract class ValueModel<DO: ValueDataObject, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): InternalModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsValueModel<DO, P> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = object: ValueDataModelSerializer<DO, P>(this as P) {}

    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    fun toBytes(vararg inputs: Any) =
        Model.toBytes(*inputs)

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: ValueDataModel<DO, P>(
        name = objClass.simpleName!!,
        properties = this@ValueModel as P,
    ) {}
}
