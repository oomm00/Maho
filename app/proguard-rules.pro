# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# WorkManager
-keep public class com.omsati.dropwatch.PriceCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Coil & OkHttp & Jsoup
-dontwarn coil.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**

# Reorderable
-keep class sh.calvin.reorderable.** { *; }

# Strip debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
