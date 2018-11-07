package maryk.node

@JsModule("crypto")
external object Crypto {
    fun randomBytes(length: Int): Buffer
}
