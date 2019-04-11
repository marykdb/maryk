package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.lib.time.DateTime
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
        val message = add(
            index = 1, name = "message",
            definition = StringDefinition()
        )
        val severity = add(
            index = 2, name = "severity",
            definition = EnumDefinition(
                final = true,
                enum = Severity,
                default = INFO
            )
        )
        val timestamp = add(
            index = 3, name = "timestamp",
            definition = DateTimeDefinition(
                final = true,
                precision = MILLIS,
                fillWithNow = true
            )
        )
    }

    operator fun invoke(
        message: String,
        severity: Severity = INFO,
        timestamp: DateTime = DateTime.nowUTC()
    ) = this.values {
        mapNonNulls(
            this.timestamp with timestamp,
            this.severity with severity,
            this.message with message
        )
    }
}
