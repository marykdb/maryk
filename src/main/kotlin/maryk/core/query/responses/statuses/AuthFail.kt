package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel

/** Authorization fail for this action */
class AuthFail<DO: Any> : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.AUTH_FAIL

    override fun equals(other: Any?) = other is AuthFail<*>
    override fun hashCode() = 0

    companion object: QueryDataModel<AuthFail<*>>(
            construct = { AuthFail<Any>() },
            definitions = listOf()
    )
}