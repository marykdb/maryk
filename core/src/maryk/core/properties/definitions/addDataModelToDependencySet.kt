package maryk.core.properties.definitions

import maryk.core.definitions.MarykPrimitive

internal fun IsDefinitionWithDataModel<*>.addDataModelToDependencySet(
    dependencySet: MutableList<MarykPrimitive>
) {
    if (!dependencySet.contains(dataModel as MarykPrimitive)) {
        // First add it so cyclical deps are not looping
        dependencySet.add(dataModel as MarykPrimitive)
        dataModel.getAllDependencies(dependencySet)
        // Then remove and add it again to have dependencies in right order. This makes sure the dependents are in front
        dependencySet.remove(dataModel as MarykPrimitive)
        dependencySet.add(dataModel as MarykPrimitive)
    }
}
