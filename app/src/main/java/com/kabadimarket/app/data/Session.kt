package com.kabadimarket.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Gujarati / Hindi / English — the SAME translations as the old app
 * (copied into assets/translations.json), same default language (Gujarati).
 */
object I18n {
    val LANGUAGES = listOf("gu" to "ગુજરાતી", "hi" to "हिंदी", "en" to "English")

    private var dict: JSONObject = JSONObject()
    private lateinit var prefs: SharedPreferences

    /** Changing this re-draws every screen in the new language. */
    var lang by mutableStateOf("gu")
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        lang = prefs.getString("language", "gu") ?: "gu"
        dict = try {
            JSONObject(context.assets.open("translations.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun setLanguage(code: String) {
        lang = code
        if (::prefs.isInitialized) prefs.edit().putString("language", code).apply()
    }

    /** Same lookup as the old app: chosen language → English → the key itself. */
    fun t(key: String): String {
        val l = lang
        dict.optJSONObject(l)?.let { if (it.has(key)) return it.optString(key) }
        dict.optJSONObject("en")?.let { if (it.has(key)) return it.optString(key) }
        return key
    }

    /** For status / condition values coming from the server. */
    fun status(value: String): String {
        val obj = dict.optJSONObject(lang) ?: return value
        val k = "status.$value"
        return if (obj.has(k)) obj.optString(k) else value
    }

    /** Extra texts that exist only in the new app. */
    fun x(key: String): String {
        val row = EXTRA[key] ?: return key
        return when (lang) {
            "gu" -> row.first
            "hi" -> row.second
            else -> row.third
        }
    }

    private val EXTRA: Map<String, Triple<String, String, String>> = mapOf(
        "err.slow" to Triple(
            "સર્વર ધીમું છે — કદાચ જાગી રહ્યું છે. એક મિનિટ પછી ફરી પ્રયાસ કરો.",
            "सर्वर धीमा है — शायद जाग रहा है। एक मिनट बाद फिर कोशिश करें।",
            "Server is slow — it may be waking up. Please try again in a minute.",
        ),
        "err.offline" to Triple(
            "સર્વર સુધી પહોંચી શકાતું નથી. ઇન્ટરનેટ તપાસો.",
            "सर्वर तक नहीं पहुंच पा रहे। इंटरनेट जांचें।",
            "Cannot reach server. Check internet and try again.",
        ),
        "err.401" to Triple("લોગિન સમાપ્ત થયું. ફરી લોગિન કરો.", "लॉगिन खत्म हो गया। फिर से लॉगिन करें।", "Login expired. Please login again."),
        "err.403" to Triple("આ માટે તમારી પાસે પરવાનગી નથી.", "इसके लिए आपके पास अनुमति नहीं है।", "You don't have permission for this."),
        "err.404" to Triple("મળ્યું નહીં.", "नहीं मिला।", "Not found."),
        "err.503" to Triple("સર્વર વ્યસ્ત છે — ફરી પ્રયાસ કરો.", "सर्वर व्यस्त है — फिर कोशिश करें।", "Server busy — please try again."),
        "err.500" to Triple("સર્વરમાં ભૂલ. ફરી પ્રયાસ કરો.", "सर्वर में गड़बड़ी। फिर कोशिश करें।", "Server error. Please try again."),
        "wakeup" to Triple(
            "સર્વર જાગી રહ્યું છે… પહેલું લોગિન 1 મિનિટ લઈ શકે.",
            "सर्वर जाग रहा है… पहला लॉगिन 1 मिनट ले सकता है।",
            "Server is waking up… first login can take up to 1 minute.",
        ),
        "soon.title" to Triple("આગલા અપડેટમાં આવશે", "अगले अपडेट में आएगा", "Coming in the next update"),
        "soon.sub" to Triple(
            "આ વિભાગ નવી એપમાં બની રહ્યો છે. ત્યાં સુધી જૂની એપ વાપરો.",
            "यह हिस्सा नई ऐप में बन रहा है। तब तक पुरानी ऐप इस्तेमाल करें।",
            "This section is being built in the new app. Until then, use the old app for it.",
        ),
        "online" to Triple("ઓનલાઇન", "ऑनलाइन", "Online"),
        "welcome" to Triple("સ્વાગત છે,", "स्वागत है,", "Welcome,"),
        "lowStock" to Triple("પાર્ટ્સનો સ્ટોક ઓછો છે — જોવા ટેપ કરો", "पार्ट्स का स्टॉक कम है — देखने के लिए टैप करें", "parts low on stock — tap to view"),
        "sticker.printing" to Triple("સ્ટીકર પ્રિન્ટિંગ", "स्टिकर प्रिंटिंग", "Sticker printing"),
        "sticker.title" to Triple("AI સ્ટીકર સ્કેનર", "AI स्टिकर स्कैनर", "AI Sticker Scanner"),
        "sticker.sub" to Triple(
            "ગેલેરી/કેમેરાથી કોઈપણ સ્ટીકર સ્કેન • પાર્ટ નંબર એડિટ • રીપ્રિન્ટ",
            "गैलरी/कैमरा से कोई भी स्टिकर स्कैन • पार्ट नंबर एडिट • रीप्रिंट",
            "Scan any sticker from gallery/camera • edit part no • reprint",
        ),
        "notFound" to Triple("મળ્યું નહીં", "नहीं मिला", "Not found"),
        "exported" to Triple("ફાઇલ તૈયાર છે", "फ़ाइल तैयार है", "File ready"),
        "saved" to Triple("સેવ થયું", "सेव हो गया", "Saved"),
    )
}

data class User(
    val id: String,
    val name: String,
    val username: String,
    val role: String,
    val storeName: String,
    val storeGst: String,
    val storePhone: String,
    val storeAddress: String,
    val contact: String,
    val storeLogo: String,
    val storeBank: String,
    val permissions: Set<String>,
) {
    val isAdmin: Boolean get() = role == "admin" || role == "super_admin"
    val isSuperAdmin: Boolean get() = role == "super_admin"
    val isStoreAdmin: Boolean get() = role == "admin"
    val isOwner: Boolean get() = contact.isNotBlank() && contact == OWNER_CONTACT

    fun can(permission: String): Boolean = isAdmin || permission in permissions

    companion object {
        const val OWNER_CONTACT = "+919773041676"

        fun from(j: JSONObject) = User(
            id = j.str("id"),
            name = j.str("name"),
            username = j.str("username"),
            role = j.str("role"),
            storeName = j.str("store_name"),
            storeGst = j.str("store_gst"),
            storePhone = j.str("store_phone"),
            storeAddress = j.str("store_address"),
            contact = j.str("contact"),
            storeLogo = j.str("store_logo"),
            storeBank = j.str("store_bank"),
            permissions = j.arr("permissions").strings().toSet(),
        )
    }
}

/** Keeps the login saved on the phone so the user doesn't log in every time. */
object Session {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        Api.token = prefs.getString("token", null)
    }

    val user: User?
        get() {
            if (Api.token == null) return null
            val raw = prefs.getString("user", null) ?: return null
            return try {
                User.from(JSONObject(raw))
            } catch (e: Exception) {
                null
            }
        }

    fun save(token: String, user: JSONObject) {
        Api.token = token
        prefs.edit().putString("token", token).putString("user", user.toString()).apply()
    }

    fun updateUser(user: JSONObject) {
        prefs.edit().putString("user", user.toString()).apply()
    }

    fun clear() {
        Api.token = null
        prefs.edit().clear().apply()
    }
}
