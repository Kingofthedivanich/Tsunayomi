# ML Kit discovers its components by instantiating registrars reflectively (Firebase component model).
# R8 full mode removes their no-arg constructors as unused, and translation fails in release builds only.
-keep class * implements com.google.firebase.components.ComponentRegistrar { public <init>(); }
