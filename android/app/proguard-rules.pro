# Google Play Services Ads (AdMob)
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-keep interface com.google.android.gms.ads.** { *; }

# JavascriptInterface preservation
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class com.gpsmap.kajal.MainActivity$AndroidNativeInterface {
    public *;
}

