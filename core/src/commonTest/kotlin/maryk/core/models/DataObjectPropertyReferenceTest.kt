package maryk.core.models

import maryk.core.properties.definitions.wrapper.at
import maryk.core.properties.definitions.wrapper.atKeyAndType
import maryk.core.properties.definitions.wrapper.refAtKey
import maryk.core.properties.definitions.wrapper.refAtKeyAndType
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.EmbeddedMarykModel.Properties
import maryk.test.models.Option.V1
import maryk.test.models.Option.V3
import maryk.test.models.TestMarykModel
import maryk.test.shouldBe
import kotlin.test.Test

internal class DataObjectPropertyReferenceTest {
    @Test
    fun testReference() {
        TestMarykModel.ref { string }.completeName shouldBe "string"
        TestMarykModel.ref { bool }.completeName shouldBe "bool"

        TestMarykModel { embeddedValues ref { value } }.completeName shouldBe "embeddedValues.value"

        TestMarykModel { embeddedValues ref { model } }.completeName shouldBe "embeddedValues.model"
        TestMarykModel { embeddedValues { model { model ref { value } } } }.completeName shouldBe "embeddedValues.model.model.value"
        TestMarykModel { embeddedValues { model { model { model ref { value } } } } }.completeName shouldBe "embeddedValues.model.model.model.value"

        TestMarykModel { embeddedValues { marykModel { list refAt 5u } } }.completeName shouldBe "embeddedValues.marykModel.list.@5"

        TestMarykModel { embeddedValues { marykModel { set refAt Date(2017, 12, 5) } } }.completeName shouldBe "embeddedValues.marykModel.set.\$2017-12-05"

        TestMarykModel { embeddedValues { marykModel { map refToKey Time(12, 23) } } }.completeName shouldBe """embeddedValues.marykModel.map.$12:23"""
        TestMarykModel { embeddedValues { marykModel { map refAt Time(12, 23) } } }.completeName shouldBe "embeddedValues.marykModel.map.@12:23"

        TestMarykModel { multi refAtType V1 }.completeName shouldBe "multi.*V1"
        TestMarykModel { multi.refToType() }.completeName shouldBe "multi.*"

        ComplexModel { mapIntObject.refAtKey(2u) { value } }.completeName shouldBe "mapIntObject.@2.value"
        ComplexModel { mapIntObject.at(2u) { model ref { value } } }.completeName shouldBe "mapIntObject.@2.model.value"

        ComplexModel { mapIntMulti.refAtKeyAndType(2u, V3, EmbeddedMarykModel.Properties) { value } }.completeName shouldBe "mapIntMulti.@2.*V3.value"
        ComplexModel { mapIntMulti.atKeyAndType(2u, V3, EmbeddedMarykModel.Properties) { model ref { value } } }.completeName shouldBe "mapIntMulti.@2.*V3.model.value"

        ComplexModel { multi.refWithType(V3, Properties) { value } }.completeName shouldBe "multi.*V3.value"
        ComplexModel { multi.withType(V3, Properties) { model ref { value } } }.completeName shouldBe "multi.*V3.model.value"
    }

    @Test
    fun testReferenceAsStorage() {
        TestMarykModel.ref { string }.toStorageByteArray().toHex() shouldBe "09"
        TestMarykModel.ref { bool }.toStorageByteArray().toHex() shouldBe "31"

        TestMarykModel { embeddedValues ref { value } }.toStorageByteArray().toHex() shouldBe "6609"

        TestMarykModel { embeddedValues ref { model } }.toStorageByteArray().toHex() shouldBe "6616"
        TestMarykModel { embeddedValues { model { model ref { value } } } }.toStorageByteArray().toHex() shouldBe "66161609"
        TestMarykModel { embeddedValues { model { model { model ref { value } } } } }.toStorageByteArray().toHex() shouldBe "6616161609"

        TestMarykModel { embeddedValues { marykModel { list refAt 5u } } }.toStorageByteArray().toHex() shouldBe "661e4200000005"

        TestMarykModel { embeddedValues { marykModel { set refAt Date(2017, 12, 5) } } }.toStorageByteArray().toHex() shouldBe "661e4b80004461"

        TestMarykModel { embeddedValues { marykModel { map refToKey Time(12, 23) } } }.toStorageByteArray().toHex() shouldBe "661e080a0300ae24"
        TestMarykModel { embeddedValues { marykModel { map refAt Time(12, 23) } } }.toStorageByteArray().toHex() shouldBe "661e540300ae24"

        TestMarykModel { multi refAtType V1 }.toStorageByteArray().toHex() shouldBe "690d"
        TestMarykModel { multi.refToType() }.toStorageByteArray().toHex() shouldBe "6905"

        ComplexModel { mapIntObject.refAtKey(2u) { value } }.toStorageByteArray().toHex() shouldBe "1c040000000209"
        ComplexModel { mapIntObject.at(2u) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "1c04000000021609"

        ComplexModel { mapIntMulti.refAtKeyAndType(2u, V3, EmbeddedMarykModel.Properties) { value } }.toStorageByteArray().toHex() shouldBe "2404000000021d09"
        ComplexModel { mapIntMulti.atKeyAndType(2u, V3, EmbeddedMarykModel.Properties) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "2404000000021d1609"

        ComplexModel { multi.refWithType(V3, Properties) { value } }.toStorageByteArray().toHex() shouldBe "091d09"
        ComplexModel { multi.withType(V3, Properties) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "091d1609"
    }
}
