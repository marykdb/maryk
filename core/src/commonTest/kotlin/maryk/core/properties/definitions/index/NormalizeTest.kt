package maryk.core.properties.definitions.index

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.SplitOn.WordBoundary
import maryk.core.properties.definitions.index.SplitOn.Whitespace
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.query.DefinitionsConversionContext
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
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

    object TokenModel : RootDataModel<TokenModel>(
        indexes = {
            listOf(
                AnyOf(
                    "name",
                    TokenModel.family.ref().normalize().split(Whitespace),
                    TokenModel { given.refToAny() }.normalize().split(Whitespace),
                )
            )
        }
    ) {
        val family by string(index = 1u, final = true)
        val given by set(
            index = 2u,
            required = false,
            final = true,
            valueDefinition = StringDefinition()
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
        expect("!Ref value") {
            checkYamlConversion(
                value = Normalize(MarykModel.value.ref()),
                dataModel = Normalize.Model,
                context = { context }
            )
        }
    }

    @Test
    fun toReferenceStorageBytes() {
        expect("170a09") { Normalize(MarykModel.value.ref()).toReferenceStorageByteArray().toHexString() }
    }

    @Test
    fun splitAndAnyOfEmitTokens() {
        val values = TokenModel.create {
            family with "van der Berg"
            given with setOf("José María")
        }

        expect(listOf("berg", "der", "jose", "maria", "van")) {
            TokenModel.Meta.indexes!![0].toStorageByteArrays(values)
                .map { it.decodeToString() }
                .sorted()
        }
    }

    @Test
    fun splitOnNameEmitsHyphenatedNameTokens() {
        val index = AnyOf(
            "name",
            TokenModel.family.ref(),
            TokenModel { given.refToAny() }
        ).normalize().split(WordBoundary)

        val values = TokenModel.create {
            family with "García-López"
            given with setOf("Jean-Luc")
        }

        expect(listOf("garcia", "jean", "lopez", "luc")) {
            index.toStorageByteArrays(values)
                .map { it.decodeToString() }
                .sorted()
        }
    }

    @Test
    fun namedSearchQueryTermsAreDedupedByContent() {
        val index = AnyOf(
            "name",
            TokenModel.family.ref(),
            TokenModel { given.refToAny() }
        ).normalize().split(WordBoundary)

        expect(listOf("garcia")) {
            index.queryToStorageByteArrays("garcia")
                .map { it.decodeToString() }
        }
    }

    @Test
    fun anyOfKeepsNameAcrossTransforms() {
        val index = AnyOf(
            "name",
            TokenModel.family.ref(),
            TokenModel { given.refToAny() }
        ).normalize().split(Whitespace)

        expect("name") { index.name }
    }

    @Test
    fun namedAnyOfIndexesShouldBeUnique() {
        assertFailsWith<IllegalArgumentException> {
            object : RootDataModel<RootDataModel<*>>(
                indexes = {
                    listOf(
                        AnyOf("name", MarykModel.value.ref()),
                        AnyOf("name", MarykModel.value.ref())
                    )
                },
                name = "DuplicateNameModel"
            ) {}.Meta
        }
    }
}
