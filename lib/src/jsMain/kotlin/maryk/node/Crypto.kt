package maryk.node

@JsModule("crypto")
@JsNonModule
external object Crypto {
    fun randomBytes(length: Int): Buffer
}
