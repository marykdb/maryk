package maryk.core.properties.definitions.index

import kotlinx.datetime.LocalDateTime
import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.RootModel
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.query.DefinitionsConversionContext
import maryk.lib.extensions.toHex
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class ReversedTest {
    object MarykModel : RootModel<MarykModel>(
        keyDefinition = {
            Multiple(
                Reversed(MarykModel.boolean.ref()),
                Reversed(MarykModel.dateTime.ref())
            )
        },
    ) {
        val boolean by boolean(1u, final = true)
        val dateTime by dateTime(
            index = 2u,
            final = true
        )
    }

    @Test
    fun testKey() {
        val dt = LocalDateTime(2017, 9, 3, 12, 43, 40)

        val obj = MarykModel.create(
            MarykModel.boolean with true,
            MarykModel.dateTime with dt
        )

        val key = MarykModel.key(obj)

        @Suppress("UNCHECKED_CAST")
        with((MarykModel.Model.keyDefinition as Multiple).references[1] as Reversed<LocalDateTime>) {
            val bc = ByteCollector()
            bc.reserve(7)
            this.writeStorageBytes(dt, bc::write)
            expect(dt) { this.readStorageBytes(bc.size, bc::read) }
        }

        expect("fe7fffffa6540703") { key.toHex() }
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = MarykModel
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = Reversed(MarykModel.boolean.ref()),
            dataModel = Reversed.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = Reversed(MarykModel.boolean.ref()),
            dataModel = Reversed.Model.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect("boolean") {
            checkYamlConversion(
                value = Reversed(MarykModel.boolean.ref()),
                dataModel = Reversed.Model.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0b09") { Reversed(MarykModel.boolean.ref()).toReferenceStorageByteArray().toHex() }
    }
}
