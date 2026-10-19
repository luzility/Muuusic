# NewPipeExtractor uses reflection / Rhino for signature deobfuscation.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn org.slf4j.**

# The widget renders through WorkManager, which creates its input merger by reflection.
-keep class * extends androidx.work.InputMerger { <init>(); }
