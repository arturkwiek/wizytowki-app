# ML Kit models
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Room
-keepnames class com.wizytowki.data.** { *; }

# Serialization
-keepclassmembers class com.wizytowki.** {
  public <fields>;
}

# Android resources
-keepclasseswithmembernames class * {
    native <methods>;
}
