package maryk.core.models

import maryk.core.properties.references.dsl.item

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.properties.references.dsl.any
import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
import maryk.core.properties.references.dsl.at
import maryk.core.properties.references.dsl.atType
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
        expect("string") { TestMarykModel.ref { string }.completeName }
        expect("bool") { TestMarykModel.ref { bool }.completeName }

        expect("embeddedValues.value") { TestMarykModel.ref { embeddedValues { value } }.completeName }

        expect("embeddedValues.model") { TestMarykModel.ref { embeddedValues { model } }.completeName }
        expect("embeddedValues.model.model.value") { TestMarykModel.ref { embeddedValues { model { model { value } } } }.completeName }
        expect("embeddedValues.model.model.model.value") { TestMarykModel.ref { embeddedValues { model { model { model { value } } } } }.completeName }

        expect("embeddedValues.marykModel.list.@5") { TestMarykModel.ref { embeddedValues { marykModel { list at 5u } } }.completeName }
        expect("embeddedValues.marykModel.list.*") { TestMarykModel.ref { embeddedValues { marykModel { list.any() } } }.completeName }

        expect("setOfString") { TestMarykModel.ref { setOfString }.completeName }
        expect("setOfString.#v1") { TestMarykModel.ref { setOfString item "v1" }.completeName }
        expect("embeddedValues.marykModel.set.#2017-12-05") { TestMarykModel.ref { embeddedValues { marykModel { set item LocalDate(2017, 12, 5) } } }.completeName }

        expect("""embeddedValues.marykModel.map.#12:23""") { TestMarykModel.ref { embeddedValues { marykModel { map key LocalTime(12, 23) } } }.completeName }
        expect("embeddedValues.marykModel.map.@12:23") { TestMarykModel.ref { embeddedValues { marykModel { map at LocalTime(12, 23) } } }.completeName }

        expect("multi.*S1") { TestMarykModel.ref { multi atType S1 }.completeName }
        expect("multi.*") { TestMarykModel.ref { multi.type }.completeName }

        expect("multi.*S3.value") { TestMarykModel.ref { multi.withType(S3) { value } }.completeName }
        expect("multi.*S3.model.value") { TestMarykModel.ref { multi.withType(S3) { model { value } } }.completeName }
        expect("multi.*S3.marykModel.set.#2017-12-05") {
            TestMarykModel.ref {
                multi.withType(S3) {
                    marykModel { set item LocalDate(2017, 12, 5) }
                }
            }.completeName
        }

        expect("mapIntObject.#2") { ComplexModel.ref { mapIntObject key 2u }.completeName }
        expect("mapIntObject.@2") { ComplexModel.ref { mapIntObject at 2u }.completeName }
        expect("mapIntObject.@2.value") { ComplexModel.ref { mapIntObject.at(2u) { value } }.completeName }
        expect("mapIntObject.*.value") { ComplexModel.ref { mapIntObject.any { value } }.completeName }
        expect("mapIntObject.@2.model.value") { ComplexModel.ref { mapIntObject.at(2u) { model { value } } }.completeName }
        expect("mapIntObject.*.model.value") { ComplexModel.ref { mapIntObject.any { model { value } } }.completeName }

        TestMarykModel.ref { reference }
        expect("reference.bool") { TestMarykModel.ref { reference { bool } }.completeName }
        expect("reference.multi.*") { TestMarykModel.ref { reference { multi.type } }.completeName }

        expect("mapWithList.@a.@23") { ComplexModel.ref { mapWithList.at("a") { at(23u) } }.completeName }

        expect("mapWithSet.@b.#b3") { ComplexModel.ref { mapWithSet.at("b") { item("b3") } }.completeName }

        expect("mapWithMap.@b.@c") { ComplexModel.ref { mapWithMap.at("b") { at("c") } }.completeName }

        expect("mapIntMulti.@2.*T3") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) } }.completeName }
        expect("mapIntMulti.@2.*T3.value") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) { value } } }.completeName }
        expect("mapIntMulti.@2.*T3.model.value") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) { model { value } } } }.completeName }

        expect("mapIntMulti.@2.*T4.@5") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T4) { at(5u) } } }.completeName }
        expect("mapIntMulti.@2.*T5.#value") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T5) { item("value") } } }.completeName }
    }

    @Test
    fun testReferenceAsStorage() {
        expect("09") { TestMarykModel.ref { string }.toStorageByteArray().toHexString() }
        expect("31") { TestMarykModel.ref { bool }.toStorageByteArray().toHexString() }

        expect("6609") { TestMarykModel.ref { embeddedValues { value } }.toStorageByteArray().toHexString() }

        expect("6616") { TestMarykModel.ref { embeddedValues { model } }.toStorageByteArray().toHexString() }
        expect("66161609") { TestMarykModel.ref { embeddedValues { model { model { value } } } }.toStorageByteArray().toHexString() }
        expect("6616161609") { TestMarykModel.ref { embeddedValues { model { model { model { value } } } } }.toStorageByteArray().toHexString() }

        expect("661e4200000005") { TestMarykModel.ref { embeddedValues { marykModel { list at 5u } } }.toStorageByteArray().toHexString() }

        expect("661e4b0480004461") { TestMarykModel.ref { embeddedValues { marykModel { set item LocalDate(2017, 12, 5) } } }.toStorageByteArray().toHexString() }
        expect("8b01") { TestMarykModel.ref { setOfString }.toStorageByteArray().toHexString() }
        expect("8b01027631") { TestMarykModel.ref { setOfString item "v1" }.toStorageByteArray().toHexString() }

        expect("661e540300ae24") { TestMarykModel.ref { embeddedValues { marykModel { map at LocalTime(12, 23) } } }.toStorageByteArray().toHexString() }

        expect("690d") { TestMarykModel.ref { multi atType S1 }.toStorageByteArray().toHexString() }
        expect("69") { TestMarykModel.ref { multi.type }.toStorageByteArray().toHexString() }

        expect("691d09") { TestMarykModel.ref { multi.withType(S3) { value } }.toStorageByteArray().toHexString() }
        expect("691d1609") { TestMarykModel.ref { multi.withType(S3) { model { value } } }.toStorageByteArray().toHexString() }
        expect("691d1e4b0480004461") {
            TestMarykModel.ref {
                multi.withType(S3) {
                    marykModel { set item LocalDate(2017, 12, 5) }
                }
            }.toStorageByteArray().toHexString()
        }

        expect("1c040000000209") { ComplexModel.ref { mapIntObject.at(2u) { value } }.toStorageByteArray().toHexString() }
        expect("1c04000000021609") { ComplexModel.ref { mapIntObject.at(2u) { model { value } } }.toStorageByteArray().toHexString() }

        expect("2c016100000017") { ComplexModel.ref { mapWithList.at("a") { at(23u) } }.toStorageByteArray().toHexString() }

        expect("340162026233") { ComplexModel.ref { mapWithSet.at("b") { item("b3") } }.toStorageByteArray().toHexString() }

        expect("3c01620163") { ComplexModel.ref { mapWithMap.at("b") { at("c") } }.toStorageByteArray().toHexString() }

        expect("2404000000021d") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) } }.toStorageByteArray().toHexString() }
        expect("2404000000021d09") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) { value } } }.toStorageByteArray().toHexString() }
        expect("2404000000021d1609") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T3) { model { value } } } }.toStorageByteArray().toHexString() }

        expect("2404000000022500000005") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T4) { at(5u) } } }.toStorageByteArray().toHexString() }
        expect("2404000000022d0576616c7565") { ComplexModel.ref { mapIntMulti.at(2u) { atType(T5) { item("value") } } }.toStorageByteArray().toHexString() }
    }
}
