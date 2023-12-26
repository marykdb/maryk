package maryk.datastore.test

import maryk.core.query.responses.statuses.IsResponseStatus
import maryk.core.query.responses.statuses.ServerFail
import kotlin.test.assertIs

inline fun <reified T: IsResponseStatus> assertStatusIs(value: Any?): T {
    if (value is ServerFail<*>) {
        value.cause?.printStackTrace()
    } else if (value !is T) {
        println("FAULTY RESPONSE: $value")
    }

    return assertIs<T>(value)
}
