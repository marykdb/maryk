package maryk.core.query.changes

import maryk.EmbeddedMarykObject
import maryk.TestMarykObject
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.query.DataModelPropertyContext
import maryk.lib.time.Time
import maryk.test.shouldBe
import kotlin.test.Test

class MapChangeTest {
    private val mapPropertyChange = MapChange(
        TestMarykObject.ref { map }.change(
            keysToDelete = setOf(
                Time(12, 33, 12)
            ),
            valuesToAdd = mapOf(
                Time(23, 0, 0) to "Test4",
                Time(5, 51, 53) to "Test5",
                Time(11, 10, 9) to "Test6"
            )
        )
    )

    private val context = DataModelPropertyContext(
        mapOf(
            TestMarykObject.name to { EmbeddedMarykObject }
        ),
        dataModel = TestMarykObject
    )

    @Test
    fun convert_to_ProtoBuf_and_back() {
        checkProtoBufConversion(this.mapPropertyChange, MapChange, { this.context })
    }

    @Test
    fun convert_to_JSON_and_back() {
        checkJsonConversion(this.mapPropertyChange, MapChange, { this.context })
    }

    @Test
    fun convert_to_YAML_and_back() {
        checkYamlConversion(this.mapPropertyChange, MapChange, { this.context }) shouldBe """
        map:
          valuesToAdd:
            23:00: Test4
            05:51:53: Test5
            11:10:09: Test6
          keysToDelete: ['12:33:12']

        """.trimIndent()
    }
}
