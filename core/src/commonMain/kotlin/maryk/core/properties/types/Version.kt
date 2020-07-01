package maryk.core.properties.types

import maryk.core.models.ValueDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt16
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
private val versionRegEx = Regex("^([0-9]+)([.]([0-9]+))?([.]([0-9]+))?$")

/**
 * A Version according to semantic versioning.
 * The internal values are UShort so it means that each part has a max 65535
 */
data class Version(
    val major: UShort,
    val minor: UShort,
    val patch: UShort
): ValueDataObject(toBytes(major, minor, patch)) {
    constructor(major: Int = 1, minor: Int = 0, patch: Int = 0) : this(major.toUShort(), minor.toUShort(), patch.toUShort())

    @Suppress("unused")
    object Properties : ObjectPropertyDefinitions<Version>() {
        val major by number(1u, Version::major, type = UInt16)
        val minor by number(2u, Version::minor, type = UInt16)
        val patch by number(3u, Version::patch, type = UInt16)
    }

    override fun toString(): String {
        val patch = if(patch != UShort.MIN_VALUE) {".${patch}" } else ""
        return "${major}.${minor}$patch"
    }

    companion object : ValueDataModel<Version, Properties>(
        name = "TestValueObject",
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Version, Properties>) = Version(
            major = values<UShort>(1u),
            minor = values(2u),
            patch = values(3u)
        )

        override fun writeJson(obj: Version, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            writer.writeString(obj.toString())
        }

        override fun readJsonToMap(reader: IsJsonLikeReader, context: IsPropertyContext?): MutableValueItems {
            if (reader.currentToken == StartDocument) {
                reader.nextToken()
            }

            return when (val token = reader.currentToken) {
                is Value<*> -> {
                    val value = token.value.toString()

                    val (major, _, minor, _, patch) = versionRegEx.matchEntire(value)?.destructured
                        ?: throw ParseException("Invalid version: $value")

                    MutableValueItems().also { items ->
                        try {
                            items += ValueItem(Properties.major.index, major.toUShort())
                            val realMinor = if (minor.isNotBlank()) minor.toUShort() else 0.toUShort()
                            items += ValueItem(Properties.minor.index, realMinor)
                            val realPatch = if (patch.isNotBlank()) patch.toUShort() else 0.toUShort()
                            items += ValueItem(Properties.patch.index, realPatch)
                        } catch (e: Throwable) {
                            throw ParseException("Invalid version: $value", e)
                        }
                    }
                }
                else -> {
                    super.readJsonToMap(reader, context)
                }
            }
        }
    }
}
