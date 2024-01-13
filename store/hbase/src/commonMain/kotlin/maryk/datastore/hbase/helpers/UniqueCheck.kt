package maryk.datastore.hbase.helpers

import maryk.core.models.IsRootDataModel
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.types.Key

class UniqueCheck<DM: IsRootDataModel>(val reference: ByteArray, val value: ByteArray, val exceptionCreator: (key: Key<DM>) -> ValidationException)
