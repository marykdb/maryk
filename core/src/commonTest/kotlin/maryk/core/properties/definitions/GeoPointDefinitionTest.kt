package maryk.core.properties.definitions

import maryk.checkJsonConversion
import maryk.checkProtoBufConversion
import maryk.checkYamlConversion
import maryk.core.properties.WriteCacheFailer
import maryk.core.properties.types.GeoPoint
import maryk.lib.exceptions.ParseException
import maryk.test.ByteCollector
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.expect

internal class GeoPointDefinitionTest {
    private val geoPointsToTest = arrayOf(
        GeoPoint(0, 0),
        GeoPoint(52.0906448, 5.1212607),
        GeoPoint(-54.8034246, -68.2969808),
        GeoPoint(-73.6318655, -126.342261),
        GeoPoint(90, 180),
        GeoPoint(-90, -180)
    )

    private val def = GeoPointDefinition()
    private val defMaxDefined = GeoPointDefinition(
        required = false,
        final = true,
        default = GeoPoint(52.0906448, 5.1212607)
    )


    @Test
    fun convertValuesToStorageBytesAndBack() {
        val bc = ByteCollector()
        for (geoPoint in geoPointsToTest) {
            bc.reserve(
                def.calculateStorageByteLength(geoPoint)
            )
            def.writeStorageBytes(geoPoint, bc::write)
            expect(geoPoint) { def.readStorageBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToTransportBytesAndBack() {
        val bc = ByteCollector()
        val cacheFailer = WriteCacheFailer()

        for (geoPoint in geoPointsToTest) {
            bc.reserve(
                def.calculateTransportByteLength(geoPoint, cacheFailer)
            )
            def.writeTransportBytes(geoPoint, cacheFailer, bc::write)
            expect(geoPoint) { def.readTransportBytes(bc.size, bc::read) }
            bc.reset()
        }
    }

    @Test
    fun convertValuesToStringAndBack() {
        for (geoPoint in geoPointsToTest) {
            val b = def.asString(geoPoint)
            expect(geoPoint) { def.fromString(b) }
        }
    }

    @Test
    fun invalidStringValueShouldThrowException() {
        assertFailsWith<ParseException> {
            def.fromString("wrong")
        }
    }

    @Test
    fun convertDefinitionToProtoBufAndBack() {
        checkProtoBufConversion(this.def, GeoPointDefinition.Model.Model)
        checkProtoBufConversion(this.defMaxDefined, GeoPointDefinition.Model.Model)
    }

    @Test
    fun convertDefinitionToJSONAndBack() {
        checkJsonConversion(this.def, GeoPointDefinition.Model.Model)
        checkJsonConversion(this.defMaxDefined, GeoPointDefinition.Model.Model)
    }

    @Test
    fun convertDefinitionToYAMLAndBack() {
        checkYamlConversion(this.def, GeoPointDefinition.Model.Model)

        expect(
            """
            required: false
            final: true
            default: 52.0906448,5.1212607

            """.trimIndent()
        ) {
            checkYamlConversion(this.defMaxDefined, GeoPointDefinition.Model.Model)
        }
    }
}
