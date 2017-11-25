package maryk.core.query.responses.statuses

import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.query.DataModelPropertyContext

internal val keyDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
        name = "key",
        index = 0,
        contextualResolver = { it!!.dataModel!!.key }
)

internal val reasonDefinition = StringDefinition(
        name = "reason",
        index = 0,
        required = true
)

internal val listOfStatuses = ListDefinition(
        name = "statuses",
        index = 1,
        required = true,
        valueDefinition = MultiTypeDefinition(
                required = true,
                getDefinition = mapOf(
                        StatusType.SUCCESS.index to SubModelDefinition(
                                required = true,
                                dataModel = Success
                        ),
                        StatusType.ADD_SUCCESS.index to SubModelDefinition(
                                required = true,
                                dataModel = AddSuccess
                        ),
                        StatusType.AUTH_FAIL.index to SubModelDefinition(
                                required = true,
                                dataModel = AuthFail
                        ),
                        StatusType.REQUEST_FAIL.index to SubModelDefinition(
                                required = true,
                                dataModel = RequestFail
                        ),
                        StatusType.SERVER_FAIL.index to SubModelDefinition(
                                required = true,
                                dataModel = ServerFail
                        ),
                        StatusType.VALIDATION_FAIL.index to SubModelDefinition(
                                required = true,
                                dataModel = ValidationFail
                        ),
                        StatusType.ALREADY_EXISTS.index to SubModelDefinition(
                                required = true,
                                dataModel = AlreadyExists
                        ),
                        StatusType.DOES_NOT_EXIST.index to SubModelDefinition(
                                required = true,
                                dataModel = DoesNotExist
                        )
                )::get
        )
)