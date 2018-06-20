package maryk.core.objects.graph

@Suppress("unused")
/** Defines an element which can be used within a graph */
interface IsGraphable<in DO> {
    val graphType: GraphType
}
