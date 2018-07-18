package maryk.core.models

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.key.UUIDKey
import maryk.core.properties.types.Key

typealias SimpleRootObjectDataModel<DO> = RootObjectDataModel<IsRootDataModel<ObjectPropertyDefinitions<DO>>, DO, ObjectPropertyDefinitions<DO>>
typealias RootObjectDataModelImpl = RootObjectDataModel<IsRootDataModel<ObjectPropertyDefinitions<Any>>, Any, ObjectPropertyDefinitions<Any>>

/**
 * ObjectDataModel defining data objects of type [DO] which is on root level so it can be stored and thus can have a [key].
 * The key is defined by passing an ordered array of key definitions.
 * If no key is defined the data model will get a UUID.
 *
 * The dataModel can be referenced by the [name] and the properties are defined by a [properties]
 */
abstract class RootObjectDataModel<DM: IsRootDataModel<P>, DO: Any, P: ObjectPropertyDefinitions<DO>>(
    name: String,
    final override val keyDefinitions: Array<FixedBytesProperty<out Any>> = arrayOf(UUIDKey),
    properties: P
) : ObjectDataModel<DO, P>(name, properties), IsTypedRootDataModel<DM, P> {
    final override val keySize = IsRootDataModel.calculateKeySize(keyDefinitions)

    /** Get Key based on [dataObject] */
    fun key(dataObject: DO): Key<DM> {
        val bytes = ByteArray(this.keySize)
        var index = 0
        for (it in this.keyDefinitions) {
            val value = it.getValue(this, dataObject)

            @Suppress("UNCHECKED_CAST")
            (it as IsFixedBytesEncodable<Any>).writeStorageBytes(value) {
                bytes[index++] = it
            }

            // Add separator
            if (index < this.keySize) {
                bytes[index++] = 1
            }
        }
        return Key(bytes)
    }
}
