package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.string
import maryk.core.query.DefinitionsConversionContext
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.expect

internal class NormalizeTest {
    object MarykModel : RootDataModel<MarykModel>(
        indexes = { listOf(Normalize(MarykModel.value.ref())) },
    ) {
        val value by string(
            index = 1u,
            final = true
        )
    }

    @Test
    fun testNormalizeEncoding() {
        with(MarykModel.Meta.indexes!![0] as Normalize) {
            val bc = ByteCollector()
            bc.reserve(15)
            writeStorageBytes(" José  García-López ", bc::write)
            expect("josegarcialopez") { readStorageBytes(bc.size, bc::read) }
            expect("6a6f73656761726369616c6f70657a") { bc.bytes!!.toHexString() }
        }
    }

    @Test
    fun testNormalizeDiverseStrings() {
        expect("oconnor") { normalizeStringForIndex("O’Connor") }
        expect("aegir") { normalizeStringForIndex("Ægir") }
        expect("emilezola") { normalizeStringForIndex("Émile Zola") }
        expect("elodie") { normalizeStringForIndex("e\u0301lodie") }
        expect("vanderwaals") { normalizeStringForIndex("van   der - Waals") }
        expect("strasse") { normalizeStringForIndex("Straße") }
        expect("dangelo") { normalizeStringForIndex("D’Angelo") }
        expect("jose") { normalizeStringForIndex("José") }
        expect("angstrom") { normalizeStringForIndex("Ångström") }
        expect("sorina") { normalizeStringForIndex("Șorîna") }
        expect("lukasz") { normalizeStringForIndex("Łukasz") }
        expect("soren") { normalizeStringForIndex("Søren") }
        expect("francoisdupont") { normalizeStringForIndex("François Dupont") }
        expect("ocasey") { normalizeStringForIndex("O'Casey") }
        expect("thorbjorn") { normalizeStringForIndex("Þorbjörn") }
        expect("daoud") { normalizeStringForIndex("Dāoūd") }
        expect("resume") { normalizeStringForIndex("Résumé") }
        expect("zoe") { normalizeStringForIndex("Zoë") }
        expect("nunez") { normalizeStringForIndex("Núñez") }
        expect("smorrebrod") { normalizeStringForIndex("Smørrebrød") }
    }

    private val context = DefinitionsConversionContext(
        propertyDefinitions = MarykModel
    )

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(
            value = Normalize(MarykModel.value.ref()),
            dataModel = Normalize.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(
            value = Normalize(MarykModel.value.ref()),
            dataModel = Normalize.Model,
            context = { context }
        )
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        expect("value") {
            checkYamlConversion(
                value = Normalize(MarykModel.value.ref()),
                dataModel = Normalize.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("0f09") { Normalize(MarykModel.value.ref()).toReferenceStorageByteArray().toHexString() }
    }
}
