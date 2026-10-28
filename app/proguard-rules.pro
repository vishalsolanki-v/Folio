# Release keep rules (plan M14). Libraries ship their own consumer rules for the rest.

# Sora Editor is LGPL-2.1: keep it unobfuscated and unmerged so it stays identifiable and replaceable (docs/plan.md §9).
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn kotlin.Cloneable$DefaultImpls
# TextMate support: tm4e reads grammars/themes with Gson and SnakeYAML into reflectively-built model classes.
-keep class org.eclipse.tm4e.** { *; }
-keep class org.jcodings.** { *; }
-keep class org.joni.** { *; }
-keep class org.snakeyaml.engine.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn org.eclipse.jdt.annotation.**

# mammoth (DOCX -> HTML) builds its converters reflectively in places; it's small, keep it whole.
-keep class org.zwobble.mammoth.** { *; }
# Only mammoth's DOCX writer (style-map embedding) uses StAX; Folio never writes DOCX.
-dontwarn javax.xml.stream.**

# PDFium: native code calls back into these classes and fields by name.
-keep class io.legere.pdfiumandroid.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# Readable stack traces in Play Console without exposing more than needed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
