package com.btgun.host.util

import android.util.Log

object AndroidLog {
    fun i(tag: String, message: String) {
        try {
            Log.i(tag, message)
        } catch (error: RuntimeException) {
            if (!error.isAndroidLogStub()) throw error
        }
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        try {
            if (error == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, error)
            }
        } catch (runtimeError: RuntimeException) {
            if (!runtimeError.isAndroidLogStub()) throw runtimeError
        }
    }
}

private fun RuntimeException.isAndroidLogStub(): Boolean =
    message?.contains("not mocked", ignoreCase = true) == true
