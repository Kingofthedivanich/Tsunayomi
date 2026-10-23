package app.tsunayomi.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the device and nothing else: no server, no automatic upload.
 * On the next start the user sees the full report and decides whether to share it, and with whom.
 */
object CrashReporter {
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context, versionName: String) {
        val file = reportFile(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(format(versionName, thread.name, error, Date())) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report of the last crash, if one is waiting to be shown. */
    fun pending(context: Context): String? = reportFile(context).takeIf { it.exists() }?.readText()?.ifBlank { null }

    fun discard(context: Context) {
        reportFile(context).delete()
    }

    internal fun format(versionName: String, threadName: String, error: Throwable, time: Date): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Tsunayomi $versionName")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(time)}")
            appendLine("Thread: $threadName")
            appendLine()
            append(stack)
        }
    }

    private fun reportFile(context: Context) = File(context.filesDir, FILE_NAME)
}
