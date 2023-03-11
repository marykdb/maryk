package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.extensions.toUnitLambda
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.EmbeddedMarykModel
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.expect

class DataObjectChangeTest {
    private val key1 = TestMarykModel.key(
        byteArrayOf(0, 0, 2, 43, 1, 0, 2)
    )

    private val subModel = TestMarykModel { embeddedValues::ref }

    private val incMapChange = IncMapChange(TestMarykModel { incMap::ref }.change(
        addValues = listOf("a","b")
    ))

    private val dataObjectChange = key1.change(
        Change(EmbeddedMarykModel(subModel) { value::ref } with "new"),
        Delete(EmbeddedMarykModel(subModel) { value::ref }),
        Check(EmbeddedMarykModel(subModel) { value::ref } with "current"),
        ObjectSoftDeleteChange(true),
        ObjectCreate,
        ListChange(
            TestMarykModel { list::ref }.change(
                addValuesToEnd = listOf(1, 2, 3)
            )
        ),
        SetChange(TestMarykModel { set::ref }.change()),
        incMapChange,
        lastVersion = 12345uL
    )

    private fun createContext() = RequestContext(
        mapOf(
            TestMarykModel.Model.name toUnitLambda { TestMarykModel.Model }
        ),
        dataModel = TestMarykModel.Model
    )

    @Test
    fun convertToProtoBufAndBack() {
        val requestContext = this.createContext()

        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, { requestContext })

        checkContext(requestContext)
    }

    @Test
    fun convertToJSONAndBack() {
        val requestContext = this.createContext()

        checkJsonConversion(this.dataObjectChange, DataObjectChange.Model, { requestContext })

        checkContext(requestContext)
    }

    @Test
    fun convertToYAMLAndBack() {
        val requestContext = this.createContext()

        expect(
            """
            key: AAACKwEAAg
            changes:
            - !Change
              embeddedValues.value: new
            - !Delete embeddedValues.value
            - !Check
              embeddedValues.value: current
            - !ObjectDelete
              isDeleted: true
            - !ObjectCreate
            - !ListChange
              list:
                addValuesToEnd: [1, 2, 3]
            - !SetChange
              set:
            - !IncMapChange
              incMap:
                addValues: [a, b]
            lastVersion: 12345

            """.trimIndent()
        ) {
            checkYamlConversion(this.dataObjectChange, DataObjectChange.Model, { requestContext })
        }

        checkContext(requestContext)
    }

    private fun checkContext(requestContext: RequestContext) {
        val collectedIncMapChanges = requestContext.getCollectedIncMapChanges()

        assertEquals(1, collectedIncMapChanges.count())
        assertEquals(incMapChange, collectedIncMapChanges.first())
    }
}
