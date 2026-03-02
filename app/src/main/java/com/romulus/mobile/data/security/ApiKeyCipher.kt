package com.romulus.mobile.data.security

interface ApiKeyCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}
