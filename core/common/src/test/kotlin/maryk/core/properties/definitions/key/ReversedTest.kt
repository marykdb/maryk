package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.definitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.query.DefinitionsContext
import maryk.lib.time.DateTime
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class ReversedTest {
    object MarykModel: RootDataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        keyDefinitions = definitions(
            Reversed(Properties.boolean),
            Reversed(Properties.dateTime)
        ),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val boolean = add(1, "bool", BooleanDefinition(
                final = true
            ))
            val dateTime = add(2, "dateTime", DateTimeDefinition(
                final = true
            ))
        }

        operator fun invoke(
            boolean: Boolean,
            dateTime: DateTime
        ) = this.map {
            mapNonNulls(
                this.boolean with boolean,
                this.dateTime with dateTime
            )
        }
    }

    @Test
    fun testKey(){
        val dt = DateTime(year = 2017, month = 9, day = 3, hour = 12, minute = 43, second = 40)

        val obj = MarykModel(
            boolean = true,
            dateTime = dt
        )

        val key = MarykModel.key(obj)

        @Suppress("UNCHECKED_CAST")
        with(MarykModel.keyDefinitions[1] as Reversed<DateTime>) {
            val bc = ByteCollector()
            bc.reserve(8)
            this.writeStorageBytes(dt, bc::write)
            this.readStorageBytes(bc.size, bc::read) shouldBe dt
        }

        key.toHex() shouldBe "fe017fffffa6540703"
    }

    private val context = DefinitionsContext(
        propertyDefinitions = MarykModel.Properties
    )

    @Test
    fun convert_definition_to_ProtoBuf_and_back() {
        checkProtoBufConversion(
            value = Reversed(MarykModel.Properties.boolean.getRef()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }

    @Test
    fun convert_definition_to_JSON_and_back() {
        checkJsonConversion(
            value = Reversed(MarykModel.Properties.boolean.getRef()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }

    @Test
    fun convert_definition_to_YAML_and_back() {
        checkYamlConversion(
            value = Reversed(MarykModel.Properties.boolean.getRef()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }
}
