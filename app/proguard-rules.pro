# KosherJava Zmanim
-keep class com.kosherjava.zmanim.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Moshi
-keep class com.zmanimclock.app.location.model.CityInfo { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
