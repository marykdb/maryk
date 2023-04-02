package maryk.core.models.serializers

import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import kotlin.io.encoding.ExperimentalEncodingApi

interface IsValueDataModelSerializer<DO: ValueDataObject, DM: IsObjectDataModel<DO>>:
    IsObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext> {
    /**
     * Read bytes from [reader] to DataObject
     * @throws [maryk.core.exceptions.DefNotFoundException] if definition needed for conversion is not found
     */
    fun readFromBytes(reader: () -> Byte): DO

    /** Creates bytes for given [inputs] */
    fun toBytes(vararg inputs: Any): ByteArray

    /**
     * Converts String [value] to DataObject
     * @throws [maryk.core.exceptions.DefNotFoundException] if definition needed for conversion is not found
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun fromBase64(value: String): DO
}
