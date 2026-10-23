package app.tsunayomi.crash

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

class CrashReporterTest {
    @Test
    fun `report has the version, thread and stack trace`() {
        val report = CrashReporter.format("1.2.3", "main", IllegalStateException("boom"), Date(0))
        assertTrue(report.startsWith("Tsunayomi 1.2.3"))
        assertTrue("Thread: main" in report)
        assertTrue("java.lang.IllegalStateException: boom" in report)
        assertTrue("at app.tsunayomi.crash.CrashReporterTest" in report)
    }
}
