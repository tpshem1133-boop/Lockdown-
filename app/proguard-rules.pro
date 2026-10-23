# LOCKDOWN ProGuard rules
# Room
-keep class com.lockdown.strictblocker.data.** { *; }
# Keep service / receiver entry points declared in the manifest
-keep class com.lockdown.strictblocker.engine.** { *; }
