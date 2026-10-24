# ONNX Runtime's native code finds its Java classes, constructors and fields by name over JNI;
# the AAR ships no rules for that, so R8 renaming them crashes the first OrtSession.run in release builds.
-keep class ai.onnxruntime.** { *; }
