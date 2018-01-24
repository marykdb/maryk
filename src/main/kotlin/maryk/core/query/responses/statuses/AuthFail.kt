package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions

/** Authorization fail for this action */
class AuthFail<DO: Any> : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.AUTH_FAIL

    override fun equals(other: Any?) = other is AuthFail<*>
    override fun hashCode() = 0
    override fun toString() = "AuthFail"

    internal companion object: QueryDataModel<AuthFail<*>>(
        properties = object : PropertyDefinitions<AuthFail<*>>() {}
    ) {
        override fun invoke(map: Map<Int, *>) = AuthFail<Any>()
    }
}