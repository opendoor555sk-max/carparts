package com.kabadimarket.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class User(
    val id: String,
    val name: String,
    val username: String,
    val role: String,
    val storeName: String,
    val permissions: Set<String>,
) {
    val isAdmin: Boolean get() = role == "admin" || role == "super_admin"

    fun can(permission: String): Boolean = isAdmin || permission in permissions

    companion object {
        fun from(j: JSONObject) = User(
            id = j.str("id"),
            name = j.str("name"),
            username = j.str("username"),
            role = j.str("role"),
            storeName = j.str("store_name"),
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
