package com.foresightlabs.aether.data.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PasscodeCryptoTest {

    @Test
    fun correctPinVerifies() {
        val verifier = PasscodeCrypto.createVerifier("123456".toCharArray())
        assertTrue(PasscodeCrypto.verify("123456".toCharArray(), verifier))
    }

    @Test
    fun wrongPinFailsVerification() {
        val verifier = PasscodeCrypto.createVerifier("123456".toCharArray())
        assertFalse(PasscodeCrypto.verify("654321".toCharArray(), verifier))
    }

    @Test
    fun verifierNeverContainsThePlainPin() {
        val verifier = PasscodeCrypto.createVerifier("123456".toCharArray())
        assertFalse(verifier.hashBase64.contains("123456"))
        assertFalse(verifier.saltBase64.contains("123456"))
    }

    @Test
    fun sameInputProducesDifferentVerifiersEachTime() {
        // Random salt per call -- two verifiers for the same PIN must not be byte-identical,
        // otherwise identical passcodes across installs would be distinguishable on disk.
        val a = PasscodeCrypto.createVerifier("123456".toCharArray())
        val b = PasscodeCrypto.createVerifier("123456".toCharArray())
        assertNotEquals(a.saltBase64, b.saltBase64)
        assertNotEquals(a.hashBase64, b.hashBase64)
        assertTrue(PasscodeCrypto.verify("123456".toCharArray(), a))
        assertTrue(PasscodeCrypto.verify("123456".toCharArray(), b))
    }

    @Test
    fun pinLengthIsRecorded() {
        val verifier = PasscodeCrypto.createVerifier("12345678".toCharArray())
        assertTrue(verifier.pinLength == 8)
    }

    @Test
    fun unrecognisedVerifierVersionFailsClosed() {
        val verifier = PasscodeCrypto.createVerifier("123456".toCharArray()).copy(version = 999)
        assertFalse(PasscodeCrypto.verify("123456".toCharArray(), verifier))
    }

    @Test
    fun malformedSaltFailsClosedRatherThanThrowing() {
        val verifier = PasscodeCrypto.createVerifier("123456".toCharArray()).copy(saltBase64 = "not valid base64!!")
        assertFalse(PasscodeCrypto.verify("123456".toCharArray(), verifier))
    }
}
