package maryk.yaml

/** Limits alias replay per YAML document to prevent resource exhaustion. */
data class YamlAliasLimits(
    val maxAliasCount: Int = 50,
    val maxExpandedTokens: Int = 100_000,
    val maxAliasDepth: Int = 10,
    val maxAliasNameLength: Int = 256
) {
    constructor(
        maxAliasCount: Int,
        maxExpandedTokens: Int,
        maxAliasDepth: Int
    ) : this(maxAliasCount, maxExpandedTokens, maxAliasDepth, 256)

    init {
        require(maxAliasCount >= 0) { "maxAliasCount must be at least 0" }
        require(maxExpandedTokens >= 0) { "maxExpandedTokens must be at least 0" }
        require(maxAliasDepth >= 0) { "maxAliasDepth must be at least 0" }
        require(maxAliasNameLength >= 1) { "maxAliasNameLength must be at least 1" }
    }
}
