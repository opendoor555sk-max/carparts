package com.nirmaan.calc

import android.content.Context

/** saving / loading the scientific calculator settings */
object SciStore {
    fun load(ctx: Context): SciOpts {
        val sp = ctx.getSharedPreferences("sci", Context.MODE_PRIVATE)
        return SciOpts().apply {
            lang = sp.getInt("lang", 0)
            angle = sp.getInt("angle", 0); mode = sp.getInt("mode", 0); dec = sp.getInt("dec", -1)
            exact = sp.getBoolean("exact", true); expStyle = sp.getInt("expStyle", 0); comma = sp.getBoolean("comma", false)
            group = sp.getInt("group", 0); mul = sp.getInt("mul", 0); div = sp.getInt("div", 0)
            keep = sp.getBoolean("keep", true); negExp = sp.getBoolean("negExp", true); abbrev = sp.getBoolean("abbrev", true)
            spacious = sp.getBoolean("spacious", true); isolate = sp.getBoolean("isolate", true); vibrate = sp.getBoolean("vibrate", false)
            prefixDen = sp.getBoolean("prefixDen", false); interval = sp.getInt("interval", 0)
        }
    }

    fun save(ctx: Context, o: SciOpts) {
        ctx.getSharedPreferences("sci", Context.MODE_PRIVATE).edit()
            .putInt("lang", o.lang).putInt("angle", o.angle).putInt("mode", o.mode).putInt("dec", o.dec)
            .putBoolean("exact", o.exact).putInt("expStyle", o.expStyle).putBoolean("comma", o.comma)
            .putInt("group", o.group).putInt("mul", o.mul).putInt("div", o.div)
            .putBoolean("keep", o.keep).putBoolean("negExp", o.negExp).putBoolean("abbrev", o.abbrev)
            .putBoolean("spacious", o.spacious).putBoolean("isolate", o.isolate).putBoolean("vibrate", o.vibrate)
            .putBoolean("prefixDen", o.prefixDen).putInt("interval", o.interval)
            .apply()
    }
}
