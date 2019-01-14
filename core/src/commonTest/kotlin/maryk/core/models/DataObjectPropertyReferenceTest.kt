@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package maryk.core.models

import maryk.core.properties.definitions.wrapper.at
import maryk.core.properties.definitions.wrapper.atWithType
import maryk.core.properties.definitions.wrapper.refAtKey
import maryk.core.properties.definitions.wrapper.refAtKeyAndType
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.models.ComplexModel
import maryk.test.models.EmbeddedMarykModel
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

        TestMarykModel { embeddedValues { marykModel { list refAt 5 } } }.completeName shouldBe "embeddedValues.marykModel.list.@5"

        TestMarykModel { embeddedValues { marykModel { set refAt Date(2017, 12, 5) } } }.completeName shouldBe "embeddedValues.marykModel.set.\$2017-12-05"

        TestMarykModel { embeddedValues { marykModel { map refToKey Time(12, 23) } } }.completeName shouldBe """embeddedValues.marykModel.map.$12:23"""
        TestMarykModel { embeddedValues { marykModel { map refAt Time(12, 23) } } }.completeName shouldBe "embeddedValues.marykModel.map.@12:23"

        TestMarykModel { multi ofType V1 }.completeName shouldBe "multi.*V1"

        ComplexModel { mapIntObject.refAtKey(2u) { value } }.completeName shouldBe "mapIntObject.@2.value"
        ComplexModel { mapIntObject.at(2u) { model ref { value } } }.completeName shouldBe "mapIntObject.@2.model.value"

        ComplexModel { mapIntMulti.refAtKeyAndType(2u, V3, EmbeddedMarykModel.Properties) { value } }.completeName shouldBe "mapIntMulti.@2.*V3.value"
        ComplexModel { mapIntMulti.atWithType(2u, V3, EmbeddedMarykModel.Properties) { model ref { value } } }.completeName shouldBe "mapIntMulti.@2.*V3.model.value"
    }
}
