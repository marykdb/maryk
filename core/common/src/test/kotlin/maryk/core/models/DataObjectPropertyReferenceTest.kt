package maryk.core.models

import maryk.TestMarykModel
import maryk.lib.time.Date
import maryk.lib.time.Time
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

        TestMarykModel { embeddedValues { marykModel { list at 5 } } }.completeName shouldBe "embeddedValues.marykModel.list.@5"
        TestMarykModel { embeddedValues { marykModel { set at Date(2017, 12, 5) } } }.completeName shouldBe "embeddedValues.marykModel.set.\$2017-12-05"

        TestMarykModel { embeddedValues { marykModel { map key Time(12, 23) } } }.completeName shouldBe """embeddedValues.marykModel.map.$12:23"""
        TestMarykModel { embeddedValues { marykModel { map at Time(12, 23) } } }.completeName shouldBe "embeddedValues.marykModel.map.@12:23"
    }
}
