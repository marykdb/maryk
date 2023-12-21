package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.properties.references.dsl.any
import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.references.dsl.refAt
import maryk.core.properties.references.dsl.refAtType
import maryk.lib.extensions.toHex
import maryk.test.models.ComplexModel
import maryk.test.models.MarykTypeEnum.T3
import maryk.test.models.MarykTypeEnum.T4
import maryk.test.models.MarykTypeEnum.T5
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.SimpleMarykTypeEnum.S3
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class DataObjectPropertyReferenceTest {
    @Test
    fun testReference() {
        expect("string") { TestMarykModel { string::ref }.completeName }
        expect("bool") { TestMarykModel { bool::ref }.completeName }

        expect("embeddedValues.value") { TestMarykModel { embeddedValues { value::ref } }.completeName }

        expect("embeddedValues.model") { TestMarykModel { embeddedValues { model::ref } }.completeName }
        expect("embeddedValues.model.model.value") { TestMarykModel { embeddedValues { model { model { value::ref } } } }.completeName }
        expect("embeddedValues.model.model.model.value") { TestMarykModel { embeddedValues { model { model { model { value::ref } } } } }.completeName }

        expect("embeddedValues.marykModel.list.@5") { TestMarykModel { embeddedValues { marykModel { list refAt 5u } } }.completeName }
        expect("embeddedValues.marykModel.list.*") { TestMarykModel { embeddedValues { marykModel { list.refToAny() } } }.completeName }

        expect("setOfString") { TestMarykModel { setOfString::ref }.completeName }
        expect("setOfString.#v1") { TestMarykModel { setOfString refAt "v1" }.completeName }
        expect("embeddedValues.marykModel.set.#2017-12-05") { TestMarykModel { embeddedValues { marykModel { set refAt LocalDate(2017, 12, 5) } } }.completeName }

        expect("""embeddedValues.marykModel.map.#12:23""") { TestMarykModel { embeddedValues { marykModel { map refToKey LocalTime(12, 23) } } }.completeName }
        expect("embeddedValues.marykModel.map.@12:23") { TestMarykModel { embeddedValues { marykModel { map refAt LocalTime(12, 23) } } }.completeName }

        expect("multi.*S1") { TestMarykModel { multi refAtType S1 }.completeName }
        expect("multi.*") { TestMarykModel { multi.refToType() }.completeName }

        expect("multi.*S3.value") { TestMarykModel { multi.withType(S3) { value::ref } }.completeName }
        expect("multi.*S3.model.value") { TestMarykModel { multi.withType(S3) { model { value::ref } } }.completeName }

        expect("mapIntObject.#2") { ComplexModel { mapIntObject refToKey 2u }.completeName }
        expect("mapIntObject.@2") { ComplexModel { mapIntObject refAt 2u }.completeName }
        expect("mapIntObject.@2.value") { ComplexModel { mapIntObject.at(2u) { value::ref } }.completeName }
        expect("mapIntObject.*.value") { ComplexModel { mapIntObject.any { value::ref } }.completeName }
        expect("mapIntObject.@2.model.value") { ComplexModel { mapIntObject.at(2u) { model { value::ref } } }.completeName }
        expect("mapIntObject.*.model.value") { ComplexModel { mapIntObject.any { model { value::ref } } }.completeName }

        TestMarykModel { reference::ref }
        expect("reference.bool") { TestMarykModel { reference { bool::ref } }.completeName }
        expect("reference.multi.*") { TestMarykModel { reference { multi.refToType() } }.completeName }

        expect("mapWithList.@a.@23") { ComplexModel { mapWithList.at("a") { refAt(23u) } }.completeName }

        expect("mapWithSet.@b.#b3") { ComplexModel { mapWithSet.at("b") { refAt("b3") } }.completeName }

        expect("mapWithMap.@b.@c") { ComplexModel { mapWithMap.at("b") { refAt("c") } }.completeName }

        expect("mapIntMulti.@2.*T3") { ComplexModel { mapIntMulti.at(2u) { refAtType(T3) } }.completeName }
        expect("mapIntMulti.@2.*T3.value") { ComplexModel { mapIntMulti.at(2u) { atType(T3) { value::ref } } }.completeName }
        expect("mapIntMulti.@2.*T3.model.value") { ComplexModel { mapIntMulti.at(2u) { atType(T3) { model { value::ref } } } }.completeName }

        expect("mapIntMulti.@2.*T4.@5") { ComplexModel { mapIntMulti.at(2u) { atType(T4) { refAt(5u) } } }.completeName }
        expect("mapIntMulti.@2.*T5.#value") { ComplexModel { mapIntMulti.at(2u) { atType(T5) { refAt("value") } } }.completeName }
    }

    @Test
    fun testReferenceAsStorage() {
        expect("09") { TestMarykModel { string::ref }.toStorageByteArray().toHex() }
        expect("31") { TestMarykModel { bool::ref }.toStorageByteArray().toHex() }

        expect("6609") { TestMarykModel { embeddedValues { value::ref } }.toStorageByteArray().toHex() }

        expect("6616") { TestMarykModel { embeddedValues { model::ref } }.toStorageByteArray().toHex() }
        expect("66161609") { TestMarykModel { embeddedValues { model { model { value::ref } } } }.toStorageByteArray().toHex() }
        expect("6616161609") { TestMarykModel { embeddedValues { model { model { model { value::ref } } } } }.toStorageByteArray().toHex() }

        expect("661e4200000005") { TestMarykModel { embeddedValues { marykModel { list refAt 5u } } }.toStorageByteArray().toHex() }

        expect("661e4b0480004461") { TestMarykModel { embeddedValues { marykModel { set refAt LocalDate(2017, 12, 5) } } }.toStorageByteArray().toHex() }
        expect("8b01") { TestMarykModel { setOfString::ref }.toStorageByteArray().toHex() }
        expect("8b01027631") { TestMarykModel { setOfString refAt "v1" }.toStorageByteArray().toHex() }

        expect("661e540300ae24") { TestMarykModel { embeddedValues { marykModel { map refAt LocalTime(12, 23) } } }.toStorageByteArray().toHex() }

        expect("690d") { TestMarykModel { multi refAtType S1 }.toStorageByteArray().toHex() }
        expect("69") { TestMarykModel { multi.refToType() }.toStorageByteArray().toHex() }

        expect("691d09") { TestMarykModel { multi.withType(S3) { value::ref } }.toStorageByteArray().toHex() }
        expect("691d1609") { TestMarykModel { multi.withType(S3) { model { value::ref } } }.toStorageByteArray().toHex() }

        expect("1c040000000209") { ComplexModel { mapIntObject.at(2u) { value::ref } }.toStorageByteArray().toHex() }
        expect("1c04000000021609") { ComplexModel { mapIntObject.at(2u) { model { value::ref } } }.toStorageByteArray().toHex() }

        expect("2c016100000017") { ComplexModel { mapWithList.at("a") { refAt(23u) } }.toStorageByteArray().toHex() }

        expect("340162026233") { ComplexModel { mapWithSet.at("b") { refAt("b3") } }.toStorageByteArray().toHex() }

        expect("3c01620163") { ComplexModel { mapWithMap.at("b") { refAt("c") } }.toStorageByteArray().toHex() }

        expect("2404000000021d") { ComplexModel { mapIntMulti.at(2u) { refAtType(T3) } }.toStorageByteArray().toHex() }
        expect("2404000000021d09") { ComplexModel { mapIntMulti.at(2u) { atType(T3) { value::ref } } }.toStorageByteArray().toHex() }
        expect("2404000000021d1609") { ComplexModel { mapIntMulti.at(2u) { atType(T3) { model { value::ref } } } }.toStorageByteArray().toHex() }

        expect("2404000000022500000005") { ComplexModel { mapIntMulti.at(2u) { atType(T4) { refAt(5u) } } }.toStorageByteArray().toHex() }
        expect("2404000000022d0576616c7565") { ComplexModel { mapIntMulti.at(2u) { atType(T5) { refAt("value") } } }.toStorageByteArray().toHex() }
    }
}
