package io.maryk.cli

import maryk.core.models.IsRootDataModel
import maryk.core.query.QueryTextParser

internal object ScanQueryParser {
    fun parseFilter(dataModel: IsRootDataModel, raw: String) = QueryTextParser.parseFilter(dataModel, raw)
    fun parseSelectGraph(dataModel: IsRootDataModel, paths: List<String>) = QueryTextParser.parseSelectGraph(dataModel, paths)
    fun parseOrder(dataModel: IsRootDataModel, tokens: List<String>) = QueryTextParser.parseOrder(dataModel, tokens)
    fun parseReferencePaths(tokens: List<String>) = QueryTextParser.parseReferencePaths(tokens)
    fun createRequestContext(dataModel: IsRootDataModel) = QueryTextParser.createRequestContext(dataModel)
}
