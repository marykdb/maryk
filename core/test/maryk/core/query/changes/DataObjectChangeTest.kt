package maryk.core.query.changes

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.key
import maryk.core.properties.definitions.contextual.DataModelReference
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

    private val subModel = TestMarykModel.ref { embeddedValues }

    private val incMapChange = IncMapChange(TestMarykModel.ref { incMap }.change(
        addValues = listOf("a","b")
    ))

    private val dataObjectChange = key1.change(
        Change(EmbeddedMarykModel.ref(subModel) { value } with "new"),
        Change(EmbeddedMarykModel.ref(subModel) { model } with null),
        Check(EmbeddedMarykModel.ref(subModel) { value } with "current"),
        ObjectSoftDeleteChange(true),
        ObjectCreate,
        ListChange(
            TestMarykModel.ref { list }.change(
                addValuesToEnd = listOf(1, 2, 3)
            )
        ),
        SetChange(TestMarykModel.ref { set }.change()),
        incMapChange,
        lastVersion = 12345uL
    )

    private fun createContext() = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun validateTest() {
        this.dataObjectChange.validate()
    }

    @Test
    fun convertToProtoBufAndBack() {
        val requestContext = this.createContext()

        checkProtoBufConversion(this.dataObjectChange, DataObjectChange, { requestContext })

        checkContext(requestContext)
    }

    @Test
    fun convertToJSONAndBack() {
        val requestContext = this.createContext()

        checkJsonConversion(this.dataObjectChange, DataObjectChange, { requestContext })

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
            - !Change
              embeddedValues.model: null
            - !Check
              embeddedValues.value: current
            - !ObjectDelete
              isDeleted: true
            - !ObjectCreate
            - !ListChange
              list:
                addValuesToEnd: [1, 2, 3]
            - !SetChange
              set: {}
            - !IncMapChange
              incMap:
                addValues: [a, b]
            lastVersion: 12345

            """.trimIndent()
        ) {
            checkYamlConversion(this.dataObjectChange, DataObjectChange, { requestContext })
        }

        checkContext(requestContext)
    }

    private fun checkContext(requestContext: RequestContext) {
        val collectedIncMapChanges = requestContext.getCollectedIncMapChanges()

        assertEquals(1, collectedIncMapChanges.count())
        assertEquals(incMapChange, collectedIncMapChanges.first())
    }
}
