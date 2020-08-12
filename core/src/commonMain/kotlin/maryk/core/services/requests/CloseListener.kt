package maryk.core.services.requests

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.services.ServiceDataModel
import maryk.core.values.ObjectValues

/** Closes a listener by [id] on the server */
data class CloseListener(
    override val id: ULong
): IsServiceRequest {
    object Properties : ObjectPropertyDefinitions<CloseListener>() {
        val id by number(1u, CloseListener::id, UInt64)
    }

    companion object : ServiceDataModel<CloseListener, Properties>(
        serviceClass = CloseListener::class,
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<CloseListener, Properties>) =
            CloseListener(
                id = values(1u)
            )
    }
}
