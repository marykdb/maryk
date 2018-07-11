package maryk.core.models

import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.definitions.FixedBytesProperty
import maryk.core.properties.types.Key

interface IsRootDataModel<P: IsPropertyDefinitions> : IsNamedDataModel<P> {
    val keyDefinitions: Array<FixedBytesProperty<out Any>>
    val keySize: Int

    /** Get Key by [base64] bytes as string representation */
    fun key(base64: String): Key<*>

    /** Get Key by byte [reader] */
    fun key(reader: () -> Byte): Key<*>

    /** Get Key by [bytes] array */
    fun key(bytes: ByteArray): Key<*>
}
