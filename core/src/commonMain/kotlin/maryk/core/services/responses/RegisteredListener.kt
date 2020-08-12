package maryk.core.services.responses

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.services.ServiceDataModel
import maryk.core.values.ObjectValues

/**
 * Registered a listener on the server for this connection
 * Contains an [id] to be able to identify request opening it and
 * with this id the listener can be closed.
 */
data class RegisteredListener(
    override val id: ULong
): IsServiceResponse {
    object Properties : ObjectPropertyDefinitions<RegisteredListener>() {
        val id by number(1u, RegisteredListener::id, UInt64)
    }

    companion object : ServiceDataModel<RegisteredListener, Properties>(
        serviceClass = RegisteredListener::class,
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<RegisteredListener, Properties>) =
            RegisteredListener(
                id = values(1u)
            )
    }
}
