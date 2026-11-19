# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# AndroidPdfViewer & PDFium Native JNI Bindings
# (Native C++ JNI callbacks cannot be traced by R8)
-keep class com.github.barteksc.pdfviewer.** { *; }
-keep class com.shockwave.** { *; }
-keep class io.legere.pdfiumandroid.** { *; }

# PDFBox-Android (Targeted rules so R8 can strip unused PDFBox modules)
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.pdmodel.encryption.*SecurityHandler {
    <init>(...);
}
-keep class com.tom_roush.pdfbox.android.PDFBoxResourceLoader { *; }
