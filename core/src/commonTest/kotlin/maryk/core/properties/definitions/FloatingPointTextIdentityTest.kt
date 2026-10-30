package maryk.core.properties.definitions

import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.query.RequestContext
import maryk.core.query.filters.Equals
import maryk.core.query.pairs.with
import maryk.core.yaml.MarykYamlReader
import maryk.json.JsonWriter
import maryk.lib.exceptions.ParseException
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object FloatingPointTextModel : RootDataModel<FloatingPointTextModel>() {
    val float by number(1u, type = Float32, required = false)
    val double by number(2u, type = Float64, required = false)
    val floats by list(3u, valueDefinition = NumberDefinition(type = Float32), required = false)
    val doubles by map(4u, keyDefinition = StringDefinition(), valueDefinition = NumberDefinition(type = Float64), required = false)
    val keyed by map(5u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition(), required = false)
    val members by set(6u, valueDefinition = NumberDefinition(type = Float32), required = false)
}

class FloatingPointTextIdentityTest {
    @Test
    fun jsonAndYamlPreserveOrdinaryNanPayloadsInScalarsListsAndMapValues() {
        for ((floatBits, doubleBits) in listOf(
            0x7fc00001 to 0x7ff8000000000001L,
            0x7fffffff to 0x7fffffffffffffffL,
            0xffc00021u.toInt() to 0xfff8000000000021uL.toLong(),
        )) {
            val original = FloatingPointTextModel.create(setDefaults = false) {
                float with Float.fromBits(floatBits)
                double with Double.fromBits(doubleBits)
                floats with listOf(Float.fromBits(floatBits))
                doubles with mapOf("value" to Double.fromBits(doubleBits))
            }
            val json = FloatingPointTextModel.Serializer.writeJson(original)
            val yaml = buildString { FloatingPointTextModel.Serializer.writeJson(original, YamlWriter { append(it) }) }
            for (read in listOf(
                FloatingPointTextModel.Serializer.readJson(json),
                FloatingPointTextModel.Serializer.readJson(MarykYamlReader(yaml)),
            )) {
                assertEquals(original.get { float }!!.toRawBits(), read.get { float }!!.toRawBits())
                assertEquals(original.get { double }!!.toRawBits(), read.get { double }!!.toRawBits())
                assertEquals(original.get { floats }!!.single().toRawBits(), read.get { floats }!!.single().toRawBits())
                assertEquals(original.get { doubles }!!.getValue("value").toRawBits(), read.get { doubles }!!.getValue("value").toRawBits())
            }
        }
    }

    @Test
    fun contextualSchemaDefaultsAndBoundsPreserveTheirDeclaredWidth() {
        val definitions = listOf(
            NumberDefinition(type = Float32, minValue = Float.fromBits(0xffc00033u.toInt()), maxValue = Float.fromBits(0x7fc00033), default = Float.fromBits(0x7fc00055)),
            NumberDefinition(type = Float64, minValue = Double.fromBits(0xfff8000000000033uL.toLong()), maxValue = Double.fromBits(0x7ff8000000000033), default = Double.fromBits(0x7ff8000000000055)),
        )
        for (original in definitions) {
            val json = buildString { NumberDefinition.Model.Serializer.writeObjectAsJson(original, JsonWriter { append(it) }, NumericContext()) }
            val yaml = buildString { NumberDefinition.Model.Serializer.writeObjectAsJson(original, YamlWriter { append(it) }, NumericContext()) }
            for (read in listOf(
                NumberDefinition.Model.Serializer.readJson(json, NumericContext()).toDataObject(),
                NumberDefinition.Model.Serializer.readJson(MarykYamlReader(yaml), NumericContext()).toDataObject(),
            )) {
                if (original.type == Float32) {
                    assertEquals((original.default as Float).toRawBits(), (read.default as Float).toRawBits())
                    assertEquals((original.minValue as Float).toRawBits(), (read.minValue as Float).toRawBits())
                    assertEquals((original.maxValue as Float).toRawBits(), (read.maxValue as Float).toRawBits())
                } else {
                    assertEquals((original.default as Double).toRawBits(), (read.default as Double).toRawBits())
                    assertEquals((original.minValue as Double).toRawBits(), (read.minValue as Double).toRawBits())
                    assertEquals((original.maxValue as Double).toRawBits(), (read.maxValue as Double).toRawBits())
                }
            }
        }
    }

    @Test
    fun textFilterRoundTripKeepsRawNanEquality() {
        val value = Double.fromBits(0x7ff8000000000033L)
        val values = FloatingPointTextModel.create(setDefaults = false) { double with value }
        val filter = Equals(FloatingPointTextModel.ref { double } with value)
        fun context() = RequestContext(mapOf(FloatingPointTextModel.Meta.name to DataModelReference(FloatingPointTextModel)), dataModel = FloatingPointTextModel)
        val json = buildString { Equals.Serializer.writeObjectAsJson(filter, JsonWriter { append(it) }, context()) }
        val yaml = buildString { Equals.Serializer.writeObjectAsJson(filter, YamlWriter { append(it) }, context()) }
        for (read in listOf(
            Equals.Serializer.readJson(json, context()).toDataObject(),
            Equals.Serializer.readJson(MarykYamlReader(yaml), context()).toDataObject(),
        )) {
            assertTrue(values.matches(read))
            assertEquals(value.toRawBits(), (read.referenceValuePairs.single().value as Double).toRawBits())
        }
    }

    @Test
    fun malformedRawNanFormsAreRejected() {
        val float = NumberDefinition(type = Float32)
        val double = NumberDefinition(type = Float64)
        for (text in listOf("NaN:0x7fc0000", "NaN:0x07fc00000", "NaN:0x7fc0000g", "NaN:0x+fc00001", "NaN:0x7f800000", "NaN:0x3f800000", "NaN:7fc00001")) {
            assertFailsWith<ParseException>(text) { float.fromString(text) }
        }
        for (text in listOf("NaN:0x7ff800000000000", "NaN:0x07ff8000000000000", "NaN:0x7ff800000000000g", "NaN:0x7ff0000000000000", "NaN:0xfff0000000000000", "NaN:0x3ff0000000000000", "NaN:7ff8000000000001")) {
            assertFailsWith<ParseException>(text) { double.fromString(text) }
        }
    }

    @Test
    fun signedZeroSurvivesScalarSchemaAndQualifierTextOnEveryPlatform() {
        val negativeZero = FloatingPointTextModel.create(setDefaults = false) {
            float with -0.0f
            double with -0.0
            keyed with mapOf(-0.0 to "negative")
            members with setOf(-0.0f)
        }
        val json = FloatingPointTextModel.Serializer.writeJson(negativeZero)
        val yaml = buildString { FloatingPointTextModel.Serializer.writeJson(negativeZero, YamlWriter { append(it) }) }
        for (read in listOf(FloatingPointTextModel.Serializer.readJson(json), FloatingPointTextModel.Serializer.readJson(MarykYamlReader(yaml)))) {
            assertEquals(Int.MIN_VALUE, read.get { float }!!.toRawBits())
            assertEquals(Long.MIN_VALUE, read.get { double }!!.toRawBits())
            assertEquals(Long.MIN_VALUE, read.get { keyed }!!.keys.single().toRawBits())
            assertEquals(Int.MIN_VALUE, read.get { members }!!.single().toRawBits())
        }
        val doubleRef = FloatingPointTextModel.ref { keyed at -0.0 }
        val floatRef = FloatingPointTextModel.ref { members item -0.0f }
        for (ref in listOf(doubleRef, floatRef)) {
            assertTrue(ref.completeName.endsWith("-0\\.0"), ref.completeName)
            assertContentEquals(ref.toStorageByteArray(), FloatingPointTextModel.getPropertyReferenceByName(ref.completeName).toStorageByteArray())
        }
        for (definition in listOf(NumberDefinition(type = Float32, default = -0.0f), NumberDefinition(type = Float64, default = -0.0))) {
            val text = buildString { NumberDefinition.Model.Serializer.writeObjectAsJson(definition, JsonWriter { append(it) }, NumericContext()) }
            val read = NumberDefinition.Model.Serializer.readJson(text, NumericContext()).toDataObject()
            if (definition.type == Float32) assertEquals(Int.MIN_VALUE, (read.default as Float).toRawBits())
            else assertEquals(Long.MIN_VALUE, (read.default as Double).toRawBits())
        }
    }

    @Test
    fun existingScalarSpellingsAndCanonicalQualifierNamesRemainStable() {
        for (value in listOf(0.0, -0.0, 1.5, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            val values = FloatingPointTextModel.create(setDefaults = false) { double with value }
            val expectedText = if (value.toRawBits() == Long.MIN_VALUE) "-0.0" else value.toString()
            val json = FloatingPointTextModel.Serializer.writeJson(values)
            assertEquals("{\"double\":\"$expectedText\"}", json)
            assertEquals(value.toRawBits(), FloatingPointTextModel.Serializer.readJson(json).get { double }!!.toRawBits())
        }
        val noncanonical = Double.fromBits(0x7ff8000000000033L)
        val reference = FloatingPointTextModel.ref { keyed at noncanonical }
        val canonical = FloatingPointTextModel.ref { keyed at Double.NaN }
        assertEquals(canonical.completeName, reference.completeName)
        assertContentEquals(canonical.toStorageByteArray(), reference.toStorageByteArray())
        val floatReference = FloatingPointTextModel.ref { members item Float.fromBits(0x7fc00033) }
        val canonicalFloat = FloatingPointTextModel.ref { members item Float.NaN }
        assertEquals(canonicalFloat.completeName, floatReference.completeName)
        assertContentEquals(canonicalFloat.toStorageByteArray(), floatReference.toStorageByteArray())
    }
}
