package com.kabadi.rate

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** the first screen a new trader sees must ask name + mobile and "OTP માંગો" – nothing else */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenTest {
    private fun all(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) all(v.getChildAt(i), out); return out
    }

    @Test fun freshInstallAsksNameAndMobile() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("none", Me.state(act))
        val views = all(act.window.decorView)
        val edits = views.filterIsInstance<EditText>()
        assertEquals(2, edits.size)                                            // name + mobile only
        val texts = views.filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.contains("OTP") })
        assertTrue(edits.all { it.hint.toString().isNotBlank() })
        println("SCREEN: " + texts.joinToString(" | "))
    }
}
