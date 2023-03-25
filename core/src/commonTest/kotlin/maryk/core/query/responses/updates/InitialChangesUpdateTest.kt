package maryk.core.query.responses.updates

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.pairs.with
import maryk.test.models.SimpleMarykModel
import kotlin.test.Test
import kotlin.test.expect

internal class InitialChangesUpdateTest {
    private val key = SimpleMarykModel.key("0ruQCs38S2QaByYof+IJgA")

    private val initialChanges = InitialChangesUpdate(
        version = 12345uL,
        changes = listOf(
            DataObjectVersionedChange(
                key = key,
                changes = listOf(
                    VersionedChanges(
                        version = 1234uL,
                        changes = listOf(
                            Change(SimpleMarykModel { value::ref } with "change")
                        )
                    )
                )
            )
        )
    )

    private val context = RequestContext(
        mapOf(
            SimpleMarykModel.Model.name toUnitLambda { SimpleMarykModel.Model }
        ),
        dataModel = SimpleMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.initialChanges, InitialChangesUpdate, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.initialChanges, InitialChangesUpdate, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            version: 12345
            changes:
            - key: 0ruQCs38S2QaByYof+IJgA
              changes:
              - version: 1234
                changes:
                - !Change
                  value: change

            """.trimIndent()
        ) {
            checkYamlConversion(this.initialChanges, InitialChangesUpdate, { this.context })
        }
    }
}
