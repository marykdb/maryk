package maryk.core.properties.definitions.key

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.key.ReversedTest.MarykModel.Properties.boolean
import maryk.core.properties.definitions.key.ReversedTest.MarykModel.Properties.dateTime
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.lib.time.DateTime
import maryk.test.ByteCollector
import maryk.test.shouldBe
import kotlin.test.Test

internal class ReversedTest {
    object MarykModel: RootDataModel<MarykModel, MarykModel.Properties>(
        name = "MarykModel",
        keyDefinition = Multiple(
            Reversed(boolean.ref()),
            Reversed(dateTime.ref())
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
        ) = this.values {
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
        with((MarykModel.keyDefinition as Multiple).references[1] as Reversed<DateTime>) {
            val bc = ByteCollector()
            bc.reserve(8)
            this.writeStorageBytes(dt, bc::write)
            this.readStorageBytes(bc.size, bc::read) shouldBe dt
        }

        key.toHex() shouldBe "fe017fffffa6540703"
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = MarykModel.Properties
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = Reversed(boolean.ref()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = Reversed(boolean.ref()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(
            value = Reversed(boolean.ref()),
            dataModel = Reversed.Model,
            context = { context }
        ) shouldBe "bool"
    }

    @Test
    fun toReferenceStorageBytes() {
        Reversed(boolean.ref()).toReferenceStorageByteArray().toHex() shouldBe "0c09"
    }
}
