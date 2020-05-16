package maryk.core.models

import maryk.core.models.migration.MigrationStatus
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.Version

interface IsRootValuesDataModel<P : PropertyDefinitions> : IsRootDataModel<P>, IsValuesDataModel<P>

interface IsRootDataModel<P : IsPropertyDefinitions> : IsNamedDataModel<P> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?
    val reservedIndices: List<UInt>?
    val reservedNames: List<String>?

    val version: Version

    val keyByteSize: Int
    val keyIndices: IntArray

    /** Get Key by [base64] bytes as string representation */
    fun key(base64: String): Key<*>

    /** Get Key by byte [reader] */
    fun key(reader: () -> Byte): Key<*>

    /** Get Key by [bytes] array */
    fun key(bytes: ByteArray): Key<*>

    /**
     * Create Property reference graph with list of graphables that are generated with [runner] on Properties
     * The graphables are sorted after generation so the RootPropRefGraph can be processed quicker.
     */
    fun graph(
        runner: P.() -> List<IsPropRefGraphNode<P>>
    ) = RootPropRefGraph(runner(this.properties).sortedBy { it.index })

    override fun isMigrationNeeded(
        storedDataModel: IsDataModel<*>,
        migrationReasons: MutableList<String>
    ): MigrationStatus {
        if (storedDataModel is IsRootDataModel<*>) {
            if (storedDataModel.version.major != this.version.major) {
                migrationReasons += "Major version was increased: ${storedDataModel.version} -> ${this.version}"
            }

            if (storedDataModel.keyDefinition !== this.keyDefinition) {
                migrationReasons += "Key definition was not the same"
            }
        } else {
            migrationReasons += "Stored model is not a root data model and compared is"
        }

        return super.isMigrationNeeded(storedDataModel, migrationReasons)
    }
}

