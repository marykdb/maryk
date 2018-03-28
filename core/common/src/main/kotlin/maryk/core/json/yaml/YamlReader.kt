package maryk.core.json.yaml

import maryk.core.extensions.isLineBreak
import maryk.core.json.ArrayType
import maryk.core.json.ExceptionWhileReadingJson
import maryk.core.json.InvalidJsonContent
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.JsonToken
import maryk.core.json.MapType
import maryk.core.json.TokenType
import maryk.core.json.ValueType
import maryk.core.properties.types.DateTime

@Suppress("FunctionName")
fun YamlReader(
    defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null,
    reader: () -> Char
) : IsJsonLikeReader =
    YamlReaderImpl(reader, defaultTag, tagMap)

/** Internal interface for the Yaml Reader functionality */
internal interface IsYamlReader {
    /** Is last character which was read */
    val lastChar: Char
    /** Holds the current char reader */
    var currentReader: YamlCharReader

    /** Reads next Char */
    fun read()
}

internal interface YamlValueType<out T: Any>: ValueType<T> {
    object Binary: YamlValueType<ByteArray>
    object Merge: YamlValueType<Nothing>
    object TimeStamp: YamlValueType<DateTime>
    object Value: YamlValueType<Nothing> //Default value
    object Yaml: YamlValueType<Nothing>
}

/** Reads YAML from the supplied [reader] */
internal class YamlReaderImpl(
    private val reader: () -> Char,
    private val defaultTag: String? = null,
    tagMap: Map<String, Map<String, TokenType>>? = null
) : IsJsonLikeReader, IsYamlReader {
    var version: String? = null

    override var currentToken: JsonToken = JsonToken.StartDocument

    override var lastChar: Char = '\u0000'
    override var currentReader: YamlCharReader = DocumentReader(this)

    private var unclaimedIndenting: Int? = null
    private var hasException: Boolean = false
    internal val tags: MutableMap<String, String> = mutableMapOf()

    private val anchorReaders = mutableListOf<AnchorReader<*>>()
    private val anchorReadersToRemove = mutableListOf<AnchorReader<*>>()

    private val tokenStack = mutableListOf<JsonToken>()
    private val storedAnchors = mutableMapOf<String, Array<JsonToken>>()

    private var tokenDepth = 0
    private var merges = mutableListOf<Merge>()

    var columnNumber = -1
    var lineNumber = 1

    private val tagMap: MutableMap<String, Map<String, TokenType>> = mutableMapOf(
        "tag:yaml.org,2002:" to mapOf(
            "str" to ValueType.String,
            "bool" to ValueType.Bool,
            "null" to ValueType.Null,
            "float" to ValueType.Float,
            "int" to ValueType.Int,
            "yaml" to YamlValueType.Yaml,
            "value" to YamlValueType.Value,
            "merge" to YamlValueType.Merge,
            "binary" to YamlValueType.Binary,
            "timestamp" to YamlValueType.TimeStamp,
            "seq" to ArrayType.Sequence,
            "set" to ArrayType.Set,
            "map" to MapType.Map,
            "omap" to MapType.OrderedMap,
            "pairs" to MapType.Pairs
        )
    )

    init {
        // Add all passed items to internal tag map
        tagMap?.let {
            if (!tagMap.contains(defaultTag)) {
                throw Exception("Default tag should be defined in tag map")
            }
            tagMap.map {
                this.tagMap[it.key] = it.value
            }
        }
    }

    override fun nextToken(): JsonToken {
        try {
            this.currentToken = try {
                if (!this.tokenStack.isEmpty()) {
                    this.tokenStack.removeAt(0)
                } else if (this.hasException) {
                    this.currentReader.handleReaderInterrupt()
                } else {
                    this.currentReader.let {
                        if (this.unclaimedIndenting != null && it is IsYamlCharWithIndentsReader) {
                            // Skip stray comments and read until first relevant character
                            if (this.lastChar == '#') {
                                while (!this.lastChar.isLineBreak()) {
                                    read()
                                }
                                this.unclaimedIndenting = skipEmptyLinesAndCommentsAndCountIndents()
                            }

                            val remainder = it.indentCount() - this.unclaimedIndenting!!
                            when {
                                remainder > 0 -> it.endIndentLevel(this.unclaimedIndenting!!, null, null)
                                remainder == 0 -> {
                                    this.unclaimedIndenting = null
                                    it.continueIndentLevel(null)
                                }
                                else -> // Indents are only left over on closing indents so should never be lower
                                    throw InvalidYamlContent("Lower indent found than previous started indents")
                            }
                        } else {
                            it.readUntilToken()
                        }
                    }
                }
            } catch (e: ExceptionWhileReadingJson) {
                this.hasException = true
                currentReader.handleReaderInterrupt()
            }

            when (currentToken) {
                is JsonToken.StartObject, is JsonToken.StartArray -> this.tokenDepth++
                is JsonToken.EndObject, is JsonToken.EndArray -> this.tokenDepth--
                is JsonToken.MergeFieldName -> {
                    this.merges.add(Merge(this.tokenDepth))
                    return this.nextToken()
                }
            }

            // Handle map merges
            this.merges.lastOrNull()?.let { merge ->
                when (merge.isWithArray) {
                    null -> {
                        merge.setStartToken(this.currentToken)
                        return this.nextToken()
                    }
                    true -> {
                        if (merge.tokenStartDepth == this.tokenDepth) {
                            this.merges.remove(merge)
                            return this.nextToken()
                        }
                        this.merges.add(Merge(
                            this.tokenDepth - 1,
                            this.currentToken
                        ))
                        return this.nextToken()
                    }
                    false -> {
                        if (merge.tokenStartDepth == this.tokenDepth) {
                            this.merges.remove(merge)
                            return this.nextToken()
                        }
                    }
                }
            }

            for (it in this.anchorReaders) {
                it.recordToken(currentToken, this.tokenDepth) {
                    this.anchorReadersToRemove.add(it)
                }
            }

            for (it in this.anchorReadersToRemove) {
                this.anchorReaders.remove(it)
            }
            this.anchorReadersToRemove.clear()

            return currentToken
        } catch (e: InvalidYamlContent) {
            e.lineNumber = this.lineNumber
            e.columnNumber = this.columnNumber
            throw e
        }
    }

    internal fun skipEmptyLinesAndCommentsAndCountIndents(): Int {
        var currentIndentCount = 0
        while(this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()

            if (this.lastChar == '#' && currentIndentCount != 0) {
                while (!this.lastChar.isLineBreak()) {
                    read()
                }
            }
        }
        return currentIndentCount
    }

    override fun skipUntilNextField() {
        val startDepth = this.tokenDepth
        do {
            nextToken()
        } while (
            !(currentToken is JsonToken.FieldName && this.tokenDepth <= startDepth)
            && currentToken !is JsonToken.Stopped
        )
    }

    override fun read() = try {
        if (lastChar.isLineBreak()) {
            lineNumber += 1
            columnNumber = 0
        } else {
            columnNumber += 1
        }
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    fun setUnclaimedIndenting(indentCount: Int?) {
        this.unclaimedIndenting = indentCount
    }

    fun hasUnclaimedIndenting() = this.unclaimedIndenting != null

    fun resolveTag(prefix: String, tag: String): TokenType {
        return when {
            prefix == "!" && tag.startsWith('<') && tag.endsWith('>') -> {
                val realTag = tag.removeSurrounding("<", ">")
                if (!realTag.contains(':')) {
                    throw InvalidYamlContent("Invalid tag $tag")
                }

                val indexOfColon = realTag.lastIndexOf(':') + 1

                this.tagMap[
                        realTag.substring(0, indexOfColon)
                ]?.get(realTag.substring(indexOfColon))
                        ?: throw InvalidYamlContent("Unknown $tag")
            }
            this.tags.containsKey(prefix) -> {
                val resolvedPrefix = this.tags[prefix]!!

                if (resolvedPrefix.startsWith("!")) {
                    return this.resolveTag(
                        "!",
                        resolvedPrefix.removePrefix("!") + tag
                    )
                }

                this.tagMap[resolvedPrefix]?.get(tag)
                        ?: throw InvalidYamlContent("Unknown tag $resolvedPrefix$tag")
            }
            prefix == "!" && !this.defaultTag.isNullOrEmpty() -> {
                this.tagMap[this.defaultTag]?.get(tag)
                        ?: throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            prefix == "!!" -> {
                this.tagMap["tag:yaml.org,2002:"]?.get(tag)
                        ?: throw InvalidYamlContent("Unknown tag $prefix$tag")
            }
            else -> throw InvalidYamlContent("Unknown tag prefix $prefix")
        }
    }

    fun pushToken(token: JsonToken) {
        this.tokenStack.add(token)
    }

    fun storeTokensForAnchor(anchor: String, tokens: Array<JsonToken>) {
        this.storedAnchors[anchor.trim()] = tokens
    }

    fun getTokensForAlias(alias: String): Array<JsonToken> {
        val trimmedAlias = alias.trim()
        if (trimmedAlias.isEmpty()) {
            throw InvalidYamlContent("Alias (*) does not contain valid name")
        }

        return this.storedAnchors[trimmedAlias] ?: throw InvalidYamlContent("Unknown alias *$trimmedAlias")
    }

    fun recordAnchors(anchorReader: AnchorReader<*>) {
        anchorReader.setTokenDepth(this.tokenDepth)
        this.anchorReaders.add(anchorReader)
    }
}

private class Merge(
    val tokenStartDepth: Int,
    startToken: JsonToken? = null
) {
    var isWithArray: Boolean? = null

    init {
        startToken?.let {
            this.setStartToken(it)
        }
    }

    fun setStartToken(token: JsonToken) {
        when (token) {
            is JsonToken.StartArray -> this.isWithArray = true
            is JsonToken.StartObject -> this.isWithArray = false
            else -> throw InvalidYamlContent("Merges should contain Maps or Sequences with maps")
        }
    }
}

/** Exception for invalid Yaml */
class InvalidYamlContent internal constructor(
    description: String
): InvalidJsonContent(description)
