package maryk.test.models

import maryk.core.models.RootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.key.Reversed
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.lib.time.DateTime
import maryk.test.models.Log.Properties.severity
import maryk.test.models.Log.Properties.timestamp
import maryk.test.models.Severity.INFO

enum class Severity(
    override val index: Int
): IndexedEnum<Severity> {
    ERROR(1), DEBUG(2), INFO(3);

    companion object: IndexedEnumDefinition<Severity>("Severity", Severity::values)
}

object Log: RootDataModel<Log, Log.Properties>(
    name = "Log",
    keyDefinitions = arrayOf(
        Reversed(timestamp),
        severity
    ),
    properties = Log.Properties
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
                enum = Severity
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
