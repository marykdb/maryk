package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.models.key
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.index.ReversedTest.MarykModel.Properties.boolean
import maryk.core.properties.definitions.index.ReversedTest.MarykModel.Properties.dateTime
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.lib.time.DateTime
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class ReversedTest {
    object MarykModel : RootDataModel<MarykModel, MarykModel.Properties>(
        keyDefinition = Multiple(
            Reversed(boolean.ref()),
            Reversed(dateTime.ref())
        ),
        properties = Properties
    ) {
        object Properties : PropertyDefinitions() {
            val boolean = add(
                1u, "bool", BooleanDefinition(
                    final = true
                )
            )
            val dateTime = add(
                2u, "dateTime", DateTimeDefinition(
                    final = true
                )
            )
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
    fun testKey() {
        val dt = DateTime(year = 2017, month = 9, day = 3, hour = 12, minute = 43, second = 40)

        val obj = MarykModel(
            boolean = true,
            dateTime = dt
        )

        val key = MarykModel.key(obj)

        @Suppress("UNCHECKED_CAST")
        with((MarykModel.keyDefinition as Multiple).references[1] as Reversed<DateTime>) {
            val bc = ByteCollector()
            bc.reserve(7)
            this.writeStorageBytes(dt, bc::write)
            expect(dt) { this.readStorageBytes(bc.size, bc::read) }
        }

        expect("fe7fffffa6540703") { key.toHex() }
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
        expect("bool") {
            checkYamlConversion(
                value = Reversed(boolean.ref()),
                dataModel = Reversed.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0b09") { Reversed(boolean.ref()).toReferenceStorageByteArray().toHex() }
    }
}
