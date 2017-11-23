package maryk.core.query

import maryk.Option
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.objects.RootDataModel
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.properties.types.toUInt64
import kotlin.test.Test

class DataObjectWithMetaDataTest {
    private val value = TestMarykObject(
            string = "name",
            int = 5123123,
            uint = 555.toUInt32(),
            double = 6.33,
            bool = true,
            enum = Option.V2,
            dateTime = DateTime(2017, 12, 5, 1, 33, 55)
    )

    private val key1 = TestMarykObject.key.getKey(this.value)

    private val dataObjectWithMetaData = DataObjectWithMetaData(
            key1,
            value,
            firstVersion = 12L.toUInt64(),
            lastVersion = 12345L.toUInt64(),
            isDeleted = false
    )

    @Suppress("UNCHECKED_CAST")
    private val context = DataModelPropertyContext(
            mapOf(
                    TestMarykObject.name to TestMarykObject
            ),
            dataModel = TestMarykObject as RootDataModel<Any>
    )

    @Test
    fun testProtoBufConversion() {
        checkProtoBufConversion(this.dataObjectWithMetaData, DataObjectWithMetaData, this.context)
    }

    @Test
    fun testJsonConversion() {
        checkJsonConversion(this.dataObjectWithMetaData, DataObjectWithMetaData, this.context)
    }
}