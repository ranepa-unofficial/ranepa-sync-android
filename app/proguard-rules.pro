# Мост JavaScript ↔ Kotlin: методы вызываются из страницы ЛК по имени.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
