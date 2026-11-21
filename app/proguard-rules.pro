# 课表网页导入：网页里的 JS 调这些方法
-keepclassmembers class com.zhishi.timetable.** { @android.webkit.JavascriptInterface <methods>; }
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
