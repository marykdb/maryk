package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class ChangeUpdateTest {
    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val changeUpdate = ChangeUpdate(
        key = key,
        version = 1uL,
        index = 5,
        changes = listOf(
            Change(SimpleMarykModel { value::ref } with "nicer value")
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.name toUnitLambda { SimpleMarykModel }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.changeUpdate, ChangeUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.changeUpdate, ChangeUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            key: 0ruQCs38S2QaByYof+IJgA
            version: 1
            index: 5
            changes:
            - !Change
              value: nicer value
            
            """.trimIndent()
        ) {
            checkYamlConversion(this.changeUpdate, ChangeUpdate, { this.context })
        }
    }
}
