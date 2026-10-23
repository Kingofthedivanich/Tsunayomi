# Types extensions implement, extend or construct
-keep class eu.kanade.tachiyomi.source.Source { public protected *; }
-keep class eu.kanade.tachiyomi.source.Source$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.ConfigurableSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.SourceFactory { public protected *; }
-keep class eu.kanade.tachiyomi.source.UnmeteredSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.FilterList { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.MangasPage { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Page { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SMangaUpdate { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.UpdateStrategy { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.HttpSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.ParsedHttpSource { public protected *; }

# Final classes and top-level functions extensions only call into
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.HttpException { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.JavaScriptEngine { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }

# Libraries extensions link against at runtime (from Mihon's app rules)
-keepattributes Signature
-keep,allowshrinking class * extends uy.kohesive.injekt.api.FullTypeReference
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class com.squareup.zstd.** { public protected *; }
# Extensions reach into these with reflection too (e.g. HttpSource.headers$delegate), so keep them whole.
-keep class eu.kanade.tachiyomi.** { *; }
-keep,allowoptimization class tachiyomi.core.common.util.lang.** { public protected *; }
-keep,allowoptimization class mihon.core.common.extensions.** { public protected *; }
-keepclassmembers class * implements java.io.Serializable {
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-dontwarn sun.misc.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
