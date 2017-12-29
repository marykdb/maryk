package maryk.core.objects

import maryk.Option
import maryk.SubMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.core.properties.ByteCollector
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.comparePropertyDefinitionWrapper
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.Time
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCache
import maryk.core.query.DataModelContext
import maryk.test.shouldBe
import kotlin.test.Test

internal class RootDataModelTest {
    @Test
    fun testKey() {
            TestMarykObject.key.getKey(
                    TestMarykObject(
                            string = "name",
                            int = 5123123,
                            uint = 555.toUInt32(),
                            double = 6.33,
                            bool = true,
                            enum = Option.V2,
                            dateTime = DateTime.nowUTC()
                    )
            ) shouldBe Bytes(
                    byteArrayOf(0, 0, 2, 43, 1, 1, 1, 0, 2)
            )
    }

    private val subModelRef = SubMarykObject.Properties.value.getRef(TestMarykObject.Properties.subModel.getRef())
    private val mapRef = TestMarykObject.ref { map }
    private val mapKeyRef = TestMarykObject { map key Time(12, 33, 44) }

    @Test
    fun testPropertyReferenceByName() {
        TestMarykObject.getPropertyReferenceByName(mapRef.completeName!!) shouldBe mapRef
        TestMarykObject.getPropertyReferenceByName(subModelRef.completeName!!) shouldBe subModelRef
    }

    @Test
    fun testPropertyReferenceByWriter() {
        val bc = ByteCollector()
        val cache = WriteCache()

        arrayOf(subModelRef, mapRef, mapKeyRef).forEach {
            bc.reserve(
                    it.calculateTransportByteLength(cache)
            )
            it.writeTransportBytes(cache, bc::write)

            TestMarykObject.getPropertyReferenceByBytes(bc.size, bc::read) shouldBe it

            bc.reset()
        }
    }

    @Test
    fun `convert definition to ProtoBuf and back`() {
        checkProtoBufConversion(TestMarykObject, RootDataModel.Model, DataModelContext(propertyDefinitions = TestMarykObject.properties),  ::compareDataModels)
    }

    @Test
    fun `convert definition to JSON and back`() {
        checkJsonConversion(TestMarykObject, RootDataModel.Model, DataModelContext(propertyDefinitions = TestMarykObject.properties), ::compareDataModels)
    }

    private fun compareDataModels(converted: RootDataModel<*, *>, original: RootDataModel<*, *>) {
        converted.name shouldBe original.name

        @Suppress("UNCHECKED_CAST")
        (converted.properties as PropertyDefinitions<Any>)
                .zip(original.properties as PropertyDefinitions<Any>)
                .forEach { (convertedWrapper, originalWrapper) ->
                    comparePropertyDefinitionWrapper(convertedWrapper, originalWrapper)
                }
    }
}