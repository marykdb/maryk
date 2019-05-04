package maryk.core.models

import maryk.core.properties.definitions.wrapper.any
import maryk.core.properties.definitions.wrapper.at
import maryk.core.properties.definitions.wrapper.atKeyAndType
import maryk.core.properties.definitions.wrapper.refAtKey
import maryk.core.properties.definitions.wrapper.refAtKeyAndType
import maryk.core.properties.definitions.wrapper.refToAny
import maryk.core.properties.definitions.wrapper.refToKeyAndIndex
import maryk.core.properties.definitions.wrapper.refToKeyAndType
import maryk.core.properties.definitions.wrapper.refToKeyTypeAndIndex
import maryk.core.properties.definitions.wrapper.refToKeyTypeAndIndexWeak
import maryk.lib.extensions.toHex
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.MultiTypeEnum
import maryk.test.models.MultiTypeEnum.T1
import maryk.test.models.MultiTypeEnum.T3
import maryk.test.models.MultiTypeEnum.T4
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
        TestMarykModel { embeddedValues { marykModel { list.refToAny() } } }.completeName shouldBe "embeddedValues.marykModel.list.*"

        TestMarykModel.ref { setOfString }.completeName shouldBe "setOfString"
        TestMarykModel { setOfString refAt "v1" }.completeName shouldBe "setOfString.\$v1"
        TestMarykModel { embeddedValues { marykModel { set refAt Date(2017, 12, 5) } } }.completeName shouldBe "embeddedValues.marykModel.set.\$2017-12-05"

        TestMarykModel { embeddedValues { marykModel { map refToKey Time(12, 23) } } }.completeName shouldBe """embeddedValues.marykModel.map.$12:23"""
        TestMarykModel { embeddedValues { marykModel { map refAt Time(12, 23) } } }.completeName shouldBe "embeddedValues.marykModel.map.@12:23"

        TestMarykModel { multi refAtType T1 }.completeName shouldBe "multi.*T1"
        TestMarykModel { multi.refToType() }.completeName shouldBe "multi.*"

        TestMarykModel { multi.refWithType(T3) { value } }.completeName shouldBe "multi.*T3.value"
        TestMarykModel { multi.withType(T3) { model ref { value } } }.completeName shouldBe "multi.*T3.model.value"

        ComplexModel { mapIntObject.refAtKey(2u) { value } }.completeName shouldBe "mapIntObject.@2.value"
        ComplexModel { mapIntObject.refToAny { value } }.completeName shouldBe "mapIntObject.*.value"
        ComplexModel { mapIntObject.at(2u) { model ref { value } } }.completeName shouldBe "mapIntObject.@2.model.value"
        ComplexModel { mapIntObject.any { model ref { value } } }.completeName shouldBe "mapIntObject.*.model.value"

        ComplexModel { mapWithList.refToKeyAndIndex("a", 23u) }.completeName shouldBe "mapWithList.@a.@23"

        ComplexModel { mapIntMulti.refToKeyAndType(2u, T3) }.completeName shouldBe "mapIntMulti.@2.*T3"
        ComplexModel { mapIntMulti.refAtKeyAndType(2u, T3, EmbeddedMarykModel.Properties) { value } }.completeName shouldBe "mapIntMulti.@2.*T3.value"
        ComplexModel { mapIntMulti.refAtKeyAndType(2u, T3) { value } }.completeName shouldBe "mapIntMulti.@2.*T3.value"
        ComplexModel { mapIntMulti.atKeyAndType(2u, T3, EmbeddedMarykModel.Properties) { model ref { value } } }.completeName shouldBe "mapIntMulti.@2.*T3.model.value"
        ComplexModel { mapIntMulti.atKeyAndType(2u, T3) { model ref { value } } }.completeName shouldBe "mapIntMulti.@2.*T3.model.value"

        ComplexModel { mapIntMulti.refToKeyTypeAndIndexWeak<UInt, MultiTypeEnum, String>(2u, T4, 5u) }.completeName shouldBe "mapIntMulti.@2.*T4.@5"
        ComplexModel { mapIntMulti.refToKeyTypeAndIndex(2u, T4, 5u) }.completeName shouldBe "mapIntMulti.@2.*T4.@5"
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

        TestMarykModel { embeddedValues { marykModel { set refAt Date(2017, 12, 5) } } }.toStorageByteArray().toHex() shouldBe "661e4b0480004461"
        TestMarykModel.ref { setOfString }.toStorageByteArray().toHex() shouldBe "8b01"
        TestMarykModel { setOfString refAt "v1" }.toStorageByteArray().toHex() shouldBe "8b01027631"

        TestMarykModel { embeddedValues { marykModel { map refAt Time(12, 23) } } }.toStorageByteArray().toHex() shouldBe "661e540300ae24"

        TestMarykModel { multi refAtType T1 }.toStorageByteArray().toHex() shouldBe "690d"
        TestMarykModel { multi.refToType() }.toStorageByteArray().toHex() shouldBe "6905"

        TestMarykModel { multi.refWithType(T3) { value } }.toStorageByteArray().toHex() shouldBe "691d09"
        TestMarykModel { multi.withType(T3) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "691d1609"

        ComplexModel { mapIntObject.refAtKey(2u) { value } }.toStorageByteArray().toHex() shouldBe "1c040000000209"
        ComplexModel { mapIntObject.at(2u) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "1c04000000021609"

        ComplexModel { mapWithList.refToKeyAndIndex("a", 23u) }.toStorageByteArray().toHex() shouldBe "2c016100000017"

        ComplexModel { mapIntMulti.refToKeyAndType(2u, T3) }.toStorageByteArray().toHex() shouldBe "2404000000021d"
        ComplexModel { mapIntMulti.refAtKeyAndType(2u, T3, EmbeddedMarykModel.Properties) { value } }.toStorageByteArray().toHex() shouldBe "2404000000021d09"
        ComplexModel { mapIntMulti.refAtKeyAndType(2u, T3) { value } }.toStorageByteArray().toHex() shouldBe "2404000000021d09"
        ComplexModel { mapIntMulti.atKeyAndType(2u, T3, EmbeddedMarykModel.Properties) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "2404000000021d1609"
        ComplexModel { mapIntMulti.atKeyAndType(2u, T3) { model ref { value } } }.toStorageByteArray().toHex() shouldBe "2404000000021d1609"

        ComplexModel { mapIntMulti.refToKeyTypeAndIndexWeak<UInt, MultiTypeEnum, String>(2u, T4, 5u) }.toStorageByteArray().toHex() shouldBe "2404000000022500000005"
        ComplexModel { mapIntMulti.refToKeyTypeAndIndex(2u, T4, 5u) }.toStorageByteArray().toHex() shouldBe "2404000000022500000005"
    }
}
