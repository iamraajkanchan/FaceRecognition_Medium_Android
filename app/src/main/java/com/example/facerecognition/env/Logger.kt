package com.example.facerecognition.env

import android.annotation.SuppressLint
import android.util.Log

class Logger {

    private val DEFAULT_TAG = "tensorflow"
    private val DEFAULT_MIN_LOG_LEVEL: Int = Log.DEBUG
    private var tag: String? = null
    private var messagePrefix: String? = null
    private var minLogLevel = DEFAULT_MIN_LOG_LEVEL

    companion object {
        @JvmStatic
        private val IGNORED_CLASS_NAMES = HashSet<String>().apply {
            add("dalvik.system.VMStack")
            add("java.lang.Thread")
            Logger::class.java.canonicalName?.let { add(it) }
        }
    }

    /**
     * Creates a Logger using the specified message prefix.
     *
     * @param messagePrefix is prepended to the text of every message.
     */
    constructor(messagePrefix: String?) {
        this.tag = DEFAULT_TAG
        this.messagePrefix = messagePrefix
    }

    /**
     * Creates a Logger with a custom tag and a custom message prefix. If the message prefix is set to
     *
     * <pre>null</pre>
     *
     * , the caller's class name is used as the prefix.
     *
     * @param tag identifies the source of a log message.
     * @param messagePrefix prepended to every message if non-null. If null, the name of the caller is
     * being used
     */
    constructor(tag: String?, messagePrefix: String?) {
        this.tag = tag
        val prefix = messagePrefix ?: getCallerSimpleName()
        this.messagePrefix = if (prefix.isNotEmpty()) "$prefix: " else prefix
    }

    /** Creates a Logger using the caller's class name as the message prefix.  */
    constructor() {
        this.tag = DEFAULT_TAG
        this.messagePrefix = null
    }

    /** Creates a Logger using the caller's class name as the message prefix.  */
    constructor(minLogLevel: Int) {
        this.tag = DEFAULT_TAG
        this.messagePrefix = null
        this.minLogLevel = minLogLevel
    }

    /**
     * Return caller's simple name.
     *
     *
     * Android getStackTrace() returns an array that looks like this: stackTrace[0]:
     * dalvik.system.VMStack stackTrace[1]: java.lang.Thread stackTrace[2]:
     * com.google.android.apps.unveil.env.UnveilLogger stackTrace[3]:
     * com.google.android.apps.unveil.BaseApplication
     *
     *
     * This function returns the simple version of the first non-filtered name.
     *
     * @return caller's simple name
     */
    private fun getCallerSimpleName(): String {
        // Get the current callstack so we can pull the class of the caller off of it.
        val stackTrace = Thread.currentThread().stackTrace
        for (elem in stackTrace) {
            val className = elem.className
            if (!IGNORED_CLASS_NAMES!!.contains(className)) {
                // We're only interested in the simple name of the class, not the complete package.
                val classParts = className.split("\\.").toTypedArray()
                return classParts[classParts.size - 1]
            }
        }
        return Logger::class.java.simpleName
    }

    fun setMinLogLevel(minLogLevel: Int) {
        this.minLogLevel = minLogLevel
    }

    fun isLoggable(logLevel: Int): Boolean {
        return logLevel >= minLogLevel || Log.isLoggable(tag, logLevel)
    }

    private fun toMessage(format: String, vararg args: Any): String? {
        return messagePrefix + if (args.isNotEmpty()) String.format(format, *args) else format
    }

    fun v(format: String, vararg args: Any?) {
        if (isLoggable(Log.VERBOSE)) {
            toMessage(format, *args as Array<out Any>)?.let { Log.v(tag, it) }
        }
    }

    @SuppressLint("LogTagMismatch")
    fun v(t: Throwable?, format: String, vararg args: Any?) {
        if (isLoggable(Log.VERBOSE)) {
            Log.v(tag, toMessage(format, *args as Array<out Any>), t)
        }
    }

    @SuppressLint("LogTagMismatch")
    fun d(format: String, vararg args: Any?) {
        if (isLoggable(Log.DEBUG)) {
            toMessage(format, *args as Array<out Any>)?.let { Log.d(tag, it) }
        }
    }

    @SuppressLint("LogTagMismatch")
    fun d(t: Throwable?, format: String, vararg args: Any?) {
        if (isLoggable(Log.DEBUG)) {
            Log.d(tag, toMessage(format, *args as Array<out Any>), t)
        }
    }

    @SuppressLint("LogTagMismatch")
    fun i(format: String, vararg args: Any?) {
        if (isLoggable(Log.INFO)) {
            toMessage(format, *args as Array<out Any>)?.let { Log.i(tag, it) }
        }
    }

    @SuppressLint("LogTagMismatch")
    fun i(t: Throwable?, format: String, vararg args: Any?) {
        if (isLoggable(Log.INFO)) {
            Log.i(tag, toMessage(format, *args as Array<out Any>), t)
        }
    }

    @SuppressLint("LogTagMismatch")
    fun w(format: String, vararg args: Any?) {
        if (isLoggable(Log.WARN)) {
            toMessage(format, *args as Array<out Any>)?.let { Log.w(tag, it) }
        }
    }

    @SuppressLint("LogTagMismatch")
    fun w(t: Throwable?, format: String, vararg args: Any?) {
        if (isLoggable(Log.WARN)) {
            Log.w(tag, toMessage(format, *args as Array<out Any>), t)
        }
    }

    @SuppressLint("LogTagMismatch")
    fun e(format: String, vararg args: Any?) {
        if (isLoggable(Log.ERROR)) {
            toMessage(format, *args as Array<out Any>)?.let { Log.e(tag, it) }
        }
    }

    @SuppressLint("LogTagMismatch")
    fun e(t: Throwable?, format: String, vararg args: Any?) {
        if (isLoggable(Log.ERROR)) {
            Log.e(tag, toMessage(format, *args as Array<out Any>), t)
        }
    }
}