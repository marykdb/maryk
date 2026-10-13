package maryk.core.query.changes

import kotlinx.datetime.LocalTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.expect

class CheckTest {
    private val valueCheck = Check(
        TestMarykModel.ref { string } with "test",
        TestMarykModel.ref { int } with 42
    )

    private val context = RequestContext(
        mapOf(
            TestMarykModel.Meta.name to DataModelReference(TestMarykModel),
        ),
        dataModel = TestMarykModel
    )

    @Test
    fun collectionChecksRoundTrip() {
        for (check in listOf(
            Check(TestMarykModel.ref { setOfString } with setOf("first", "second")),
            Check(TestMarykModel.ref { list } with listOf(1, 2)),
            Check(TestMarykModel.ref { map } with mapOf(LocalTime(1, 0) to "first", LocalTime(2, 0) to "second")),
            Check(TestMarykModel.ref { setOfString } with emptySet<String>(), TestMarykModel.ref { list } with emptyList<Int>(), TestMarykModel.ref { map } with emptyMap<LocalTime, String>()),
        )) {
            checkProtoBufConversion(check, Check, { context })
            checkJsonConversion(check, Check, { context })
            checkYamlConversion(check, Check, { context })
        }
    }

    @Test
    fun convertToProtoBufAndBack() {
        checkProtoBufConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToJSONAndBack() {
        checkJsonConversion(this.valueCheck, Check, { this.context })
    }

    @Test
    fun convertToYAMLAndBack() {
        expect(
            """
            string: test
            int: 42

            """.trimIndent()
        ) {
            checkYamlConversion(this.valueCheck, Check, { this.context })
        }
    }
}
