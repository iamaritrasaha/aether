package com.foresightlabs.aether.calls.media

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Call diagnostics must make a connect-time failure locatable without ever
 * carrying anything that would be dangerous to write to a log.
 */
class CallDiagnosticsTest {

    private val lines = mutableListOf<String>()
    private lateinit var original: (String) -> Unit

    @Before
    fun captureSink() {
        original = CallDiagnostics.sink
        CallDiagnostics.sink = { lines += it }
    }

    @After
    fun restoreSink() {
        CallDiagnostics.sink = original
    }

    @Test
    fun aStageLineNamesItsSessionAndStage() {
        CallDiagnostics.stage(7L, CallStage.MEDIA_CONNECTED, "kind=NORMAL")
        assertEquals(listOf("gen=7 stage=MEDIA_CONNECTED kind=NORMAL"), lines)
    }

    /** The last stage reached before a crash is the whole point of this. */
    @Test
    fun theConnectSequenceIsObservableInOrder() {
        val expected = listOf(
            CallStage.TDLIB_READY,
            CallStage.MEDIA_SESSION_CREATED,
            CallStage.P2P_CONNECTING,
            CallStage.AUDIO_INITIALIZING,
            CallStage.VIDEO_INITIALIZING,
            CallStage.MEDIA_CONNECTED,
            CallStage.UI_ACTIVE
        )
        expected.forEach { CallDiagnostics.stage(1L, it) }

        assertEquals(expected.size, lines.size)
        expected.forEachIndexed { index, stage ->
            assertTrue(lines[index].contains("stage=${stage.name}"))
        }
    }

    @Test
    fun aFailureReportsItsTypeAndTheStageItHappenedAt() {
        CallDiagnostics.failure(3L, CallStage.AUDIO_INITIALIZING, IllegalStateException("device busy"))
        val line = lines.single()

        assertTrue(line.contains("stage=FAILED"))
        assertTrue(line.contains("at=AUDIO_INITIALIZING"))
        assertTrue(line.contains("java.lang.IllegalStateException"))
        assertTrue(line.contains("device busy"))
    }

    /**
     * Key-shaped material is redacted even when it arrives inside an exception
     * message, because that is the one path a caller does not control.
     */
    @Test
    fun keyShapedMaterialIsRedacted() {
        val key = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkw"
        val sanitised = CallDiagnostics.sanitise("failed with key $key while connecting")

        assertFalse("the key survived redaction", sanitised.contains(key))
        assertTrue(sanitised.contains("<redacted:"))
        assertTrue(sanitised.contains("while connecting"))
    }

    @Test
    fun ordinaryWordsAreNotRedacted() {
        val sanitised = CallDiagnostics.sanitise("microphone device was not available")
        assertEquals("microphone device was not available", sanitised)
    }

    @Test
    fun detailIsLengthCappedSoALineStaysALandmarkNotADump() {
        val dump = List(400) { "word" }.joinToString(" ")
        val sanitised = CallDiagnostics.sanitise(dump)
        assertTrue("length was ${sanitised.length}", sanitised.length <= 161)
    }

    @Test
    fun newlinesNeverSplitOneEventAcrossSeveralLines() {
        val sanitised = CallDiagnostics.sanitise("first\nsecond\nthird")
        assertFalse(sanitised.contains("\n"))
    }

    @Test
    fun aNullThrowableStillProducesAUsableLine() {
        CallDiagnostics.failure(2L, CallStage.TEARDOWN, null)
        val line = lines.single()
        assertTrue(line.contains("stage=FAILED"))
        assertTrue(line.contains("type=unknown"))
    }
}
