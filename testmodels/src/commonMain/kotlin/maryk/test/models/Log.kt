package maryk.test.models

import kotlinx.datetime.LocalDateTime
import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.test.models.Log.Properties
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Log.Properties.timestamp
import maryk.test.models.Severity.INFO

sealed class Severity(
    index: UInt
) : IndexedEnumImpl<Severity>(index) {
    object INFO: Severity(1u)
    object DEBUG: Severity(2u)
    object ERROR: Severity(3u)
    class UnknownSeverity(index: UInt, override val name: String): Severity(index)

    companion object : IndexedEnumDefinition<Severity>(
        Severity::class, { arrayOf(INFO, DEBUG, ERROR) }, unknownCreator = ::UnknownSeverity
    )
}

object Log : RootDataModel<Log, Properties>(
    keyDefinition = Multiple(
        Reversed(timestamp.ref()),
        severity.ref()
    ),
    indices = listOf(
        severity.ref()
    ),
    properties = Properties
) {
    object Properties : PropertyDefinitions() {
        val message by string(
            index = 1u
        )
        val severity by enum(
            index = 2u,
            final = true,
            enum = Severity,
            default = INFO
        )
        val timestamp by dateTime(
            index = 3u,
            final = true,
            precision = MILLIS
        )
    }

    operator fun invoke(
        message: String,
        severity: Severity = INFO,
        timestamp: LocalDateTime = DateTimeDefinition.nowUTC()
    ) = this.values {
        mapNonNulls(
            this.timestamp with timestamp,
            this.severity with severity,
            this.message with message
        )
    }
}
