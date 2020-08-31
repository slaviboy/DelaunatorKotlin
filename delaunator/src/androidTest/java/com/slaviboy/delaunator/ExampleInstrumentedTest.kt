package com.slaviboy.delaunator

import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Test
import org.junit.Assert.*

class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.slaviboy.delaunator.test", appContext.packageName)
    }
}