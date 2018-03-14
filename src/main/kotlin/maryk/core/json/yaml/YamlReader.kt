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
        if (this.hasException) {
            return this.currentReader.handleReaderInterrupt()
        }

        currentToken = try {
            this.currentReader.let {
                if (this.unclaimedIndenting != null && it is IsYamlCharWithIndentsReader) {
                    // Skip stray comments and read until first relevant character
                    if (this.lastChar == '#') {
                        skipComments()
                    }

                    val remainder = it.indentCount() - this.unclaimedIndenting!!
                    when {
                        remainder > 0 -> it.endIndentLevel(this.unclaimedIndenting!!, null)
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
        } catch (e: ExceptionWhileReadingJson) {
            this.hasException = true
            currentReader.handleReaderInterrupt()
        }
        return currentToken
    }

    private fun skipComments() {
        while (!this.lastChar.isLineBreak()) {
            read()
        }
        var currentIndentCount = 0
        while (this.lastChar.isWhitespace()) {
            if (this.lastChar.isLineBreak()) {
                currentIndentCount = 0
            } else {
                currentIndentCount++
            }
            read()
            // Skip comments since they can start early
            if (this.lastChar == '#') {
                while (!this.lastChar.isLineBreak()) {
                    read()
                }
            }
        }
        this.unclaimedIndenting = currentIndentCount
    }

    override fun skipUntilNextField() {
        TODO("not implemented")
    }

    override fun read() = try {
        lastChar = reader()
    } catch (e: Throwable) { // Reached end or something bad happened
        throw ExceptionWhileReadingJson()
    }

    fun hasUnclaimedIndenting(indentCount: Int?) {
        this.unclaimedIndenting = indentCount
    }

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
}

/** Exception for invalid Yaml */
class InvalidYamlContent internal constructor(
    description: String
): InvalidJsonContent(description)
