package com.callrecorder.app.util

import android.util.Log
import com.callrecorder.app.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * SafeLog: masks sensitive info (phone numbers, file names) in logs.
 * Debug builds: full logs with masking.
 * Release builds: only warnings and errors are logged.
 */
object SafeLog {

    fun setUser(userId: String?, provider: String? = null, email: String? = null) {
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setUserId(userId.orEmpty())
                setCustomKey("login_provider", provider.orEmpty())
                setCustomKey("account_email", mask(email.orEmpty()))
            }
        }
    }

    fun d(tag: String, msg: String) {
        val safe = mask(msg)
        if (BuildConfig.DEBUG) Log.d(tag, safe)
    }

    fun i(tag: String, msg: String) {
        val safe = mask(msg)
        if (BuildConfig.DEBUG) Log.i(tag, safe)
    }

    fun w(tag: String, msg: String, e: Throwable? = null) {
        val safe = mask(msg)
        if (e != null) Log.w(tag, safe, e) else Log.w(tag, safe)
        recordRemote("WARN", tag, safe, e)
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        val safe = mask(msg)
        if (e != null) Log.e(tag, safe, e) else Log.e(tag, safe)
        recordRemote("ERROR", tag, safe, e)
    }

    private fun recordRemote(level: String, tag: String, msg: String, e: Throwable?) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("$level/$tag: $msg")
            crashlytics.setCustomKey("last_log_level", level)
            crashlytics.setCustomKey("last_log_tag", tag)
            crashlytics.recordException(e ?: RuntimeException("$level/$tag: $msg"))
        }
    }

    private fun mask(msg: String): String {
        var result = msg
        val phoneRegex = Regex("(\\d{3})[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})")
        result = phoneRegex.replace(result) { m ->
            m.groupValues[1] + "-****-" + m.groupValues[3]
        }
        val koreanWord = "\uD1B5\uD654\u0020\uB179\uC74C"
        val nameRegex = Regex("(" + koreanWord + "\\s+)([^_/\\\\]+?)(_\\d{6,})")
        result = nameRegex.replace(result) { m ->
            m.groupValues[1] + "***" + m.groupValues[3]
        }
        return result
    }
}
