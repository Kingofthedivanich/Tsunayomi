# commons-compress references optional codecs we never use (we only read ZIP/CBZ).
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**

# junrar for RAR/CBR archives
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.commons.io.**
-dontwarn org.slf4j.**
