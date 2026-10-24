# Keep Kotatsu parser reflection and model classes
-keep class org.koitharu.kotatsu.parsers.** { *; }
-keep interface org.koitharu.kotatsu.parsers.** { *; }
-keepclassmembers enum org.koitharu.kotatsu.parsers.model.MangaParserSource { *; }
-dontwarn org.koitharu.kotatsu.parsers.**

# Allowlist serialization
-keep class app.tsunayomi.source.kotatsu.KotatsuAllowlistEntry { *; }
