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
    private fun walk(v: View, out: MutableList<View> = mutableListOf()): List<View> {
        out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), out); return out
    }

    @Test fun freshInstallAsksNameAndMobile() {
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("none", Me.state(act))
        val views = walk(act.window.decorView)
        val edits = views.filterIsInstance<EditText>()
        assertEquals(2, edits.size)                                            // name + mobile only
        val texts = views.filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.contains("OTP") })
        assertTrue(edits.all { it.hint.toString().isNotBlank() })
        println("SCREEN: " + texts.joinToString(" | "))
    }

    @Test fun activeWithoutFormWaitsThenShowsOnlyChosenMetals() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        Me.register(ctx, "Ravi Traders", "9876543210", 1000L); Me.activate(ctx)
        var act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(0, walk(act.window.decorView).filterIsInstance<EditText>().size)     // no tiles yet
        assertTrue(walk(act.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("ફોર્મ") })
        val f = org.json.JSONObject("{\"fv\":5,\"rt\":1000,\"form\":[{\"id\":\"copper\",\"gu\":\"તાંબુ\",\"hi\":\"\",\"en\":\"Copper\",\"u\":\"kg\"},{\"id\":\"lead\",\"gu\":\"સીસું\",\"hi\":\"\",\"en\":\"Lead\",\"u\":\"kg\"}]}")
        assertTrue(Me.takeForm(ctx, f))
        assertTrue(!Me.takeForm(ctx, f))                                                  // same version: ignored
        act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals(2, walk(act.window.decorView).filterIsInstance<EditText>().size)     // copper + lead only
        Me.reset(ctx); assertTrue(!Me.hasForm(ctx))
    }
}
