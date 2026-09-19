package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class VersionedChangesTest {
    private val subModelValue = TestMarykModel.ref { embeddedValues { value } }

    private val versionedChanges = VersionedChanges(
        219674127uL,
        listOf(
            Change(subModelValue with "new"),
            Change(subModelValue  with null),
            Check(subModelValue with "current"),
            ObjectSoftDeleteChange(true),
            ListChange(TestMarykModel.ref { list }.change()),
            SetChange(TestMarykModel.ref { set }.change()),
            ObjectCreate
        )
    )

    private val context = RequestContext(
        dataModels = mapOf(
            EmbeddedMarykModel.Meta.name to DataModelReference(EmbeddedMarykModel),
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertsIndexChangeToProtoBufAndBack() {
        checkProtoBufConversion(
            VersionedChanges(
                version = 1uL,
                changes = listOf(
                    IndexChange(
                        listOf(
                            IndexDelete(
                                index = Bytes(byteArrayOf(1)),
                                indexKey = Bytes(byteArrayOf(2))
                            )
                        )
                    )
                )
            ),
            VersionedChanges,
            { this.context }
        )
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.versionedChanges, VersionedChanges, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            version: 219674127
            changes:
            - !Change
              embeddedValues.value: new
            - !Change
              embeddedValues.value: null
            - !Check
              embeddedValues.value: current
            - !ObjectDelete
              isDeleted: true
            - !ListChange
              list: {}
            - !SetChange
              set: {}
            - !ObjectCreate

            """.trimIndent()
        ) {
            checkYamlConversion(this.versionedChanges, VersionedChanges, { this.context })
        }
    }
}
