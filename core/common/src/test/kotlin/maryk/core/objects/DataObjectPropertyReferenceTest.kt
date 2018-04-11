package maryk.core.objects

import maryk.TestMarykObject
import maryk.lib.time.Date
import maryk.lib.time.Time
import maryk.test.shouldBe
import kotlin.test.Test

internal class DataObjectPropertyReferenceTest {
    @Test
    fun testReference() {
        TestMarykObject.ref { string }.completeName shouldBe "string"
        TestMarykObject.ref { bool }.completeName shouldBe "bool"

        TestMarykObject { subModel ref { value } }.completeName shouldBe "subModel.value"

        TestMarykObject { subModel ref { model } }.completeName shouldBe "subModel.model"
        TestMarykObject { subModel { model { model ref { value } } } }.completeName shouldBe "subModel.model.model.value"
        TestMarykObject { subModel { model { model { model ref { value } } } } }.completeName shouldBe "subModel.model.model.model.value"

        TestMarykObject { subModel { marykModel { list at 5 } } }.completeName shouldBe "subModel.marykModel.list.@5"
        TestMarykObject { subModel { marykModel { set at Date(2017, 12, 5) } } }.completeName shouldBe "subModel.marykModel.set.\$2017-12-05"

        TestMarykObject { subModel { marykModel { map key Time(12, 23) } } }.completeName shouldBe "subModel.marykModel.map.\$12:23"
        TestMarykObject { subModel { marykModel { map at Time(12, 23) } } }.completeName shouldBe "subModel.marykModel.map.@12:23"
    }
}
