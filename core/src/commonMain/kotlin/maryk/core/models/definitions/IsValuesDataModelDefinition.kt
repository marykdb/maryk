package maryk.core.models.definitions

import maryk.core.models.IsValuesDataModel

interface IsValuesDataModelDefinition<DM : IsValuesDataModel> : IsNamedDataModelDefinition<DM> {
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?

    /** Check the property values */
    fun check() {
        this.reservedIndices?.let { reservedIndices ->
            this.properties.forEach { property ->
                require(!reservedIndices.contains(property.index)) {
                    "Model $name has ${property.index} defined in option ${property.name} while it is reserved"
                }
            }
        }
        this.reservedNames?.let { reservedNames ->
            this.properties.forEach { case ->
                require(!reservedNames.contains(case.name)) {
                    "Model $name has a reserved name defined ${case.name}"
                }
            }
        }
    }
}
