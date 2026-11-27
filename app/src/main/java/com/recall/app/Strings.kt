package com.recall.app

import android.content.Context

/**
 * Lightweight translation table. Keys are the English source strings; looked
 * up against the active language and falling back to the English key when
 * a translation is missing.
 *
 * This avoids the full Android strings.xml / R.string ceremony — handy for a
 * demo app with hundreds of hardcoded labels — while still giving a single
 * place to add translations as the app grows.
 */
object Strings {
    const val KEY_LANG = "lang"
    const val LANG_EN = "en"
    const val LANG_AR = "ar"

    fun current(context: Context): String =
        context.getSharedPreferences("recall_prefs", Context.MODE_PRIVATE)
            .getString(KEY_LANG, LANG_EN) ?: LANG_EN

    fun isArabic(context: Context): Boolean = current(context) == LANG_AR

    fun setLang(context: Context, lang: String) {
        context.getSharedPreferences("recall_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
    }

    fun t(context: Context, en: String): String {
        if (!isArabic(context)) return en
        return AR[en] ?: en
    }

    // English → Arabic translations for the most visible labels + button text.
    // Toasts and transient messages are not translated yet (next iteration).
    private val AR: Map<String, String> = mapOf(
        // Brand + header
        "Always-on audio buffer · recover anything you missed"
            to "مخزن صوتي دائم · استرجع كل ما فاتك",

        // Floating buy pill
        "+ BUY MIN" to "+ شراء دقائق",

        // Balance card
        "YOUR BALANCE" to "رصيدك",
        "No minutes left — tap BUY MINUTES"
            to "لا يوجد دقائق متبقية — اضغط شراء دقائق",

        // Status card
        "Listening" to "يستمع الآن",
        "Starting up…" to "جارٍ التشغيل…",
        "Listening is off" to "الاستماع متوقف",

        // Always-listening card
        "Always listening" to "الاستماع الدائم",
        "ON" to "تشغيل",
        "OFF" to "إيقاف",

        // Buffer window card
        "BUFFER WINDOW" to "نافذة التخزين",
        "Default · tap to extend further" to "افتراضي · اضغط للتمديد",
        "Extended · tap to change" to "ممتد · اضغط للتغيير",
        "Custom window · tap to change" to "مخصص · اضغط للتغيير",
        "EXTEND ▸" to "◂ تمديد",

        // Alerts card
        "ALERTS" to "التنبيهات",
        "Watchdog on" to "المراقبة مفعّلة",
        "Weekly check-in" to "فحص أسبوعي",
        "Watchdog + Weekly" to "مراقبة + فحص أسبوعي",
        "All silent" to "صامت تمامًا",
        "Pings you only if RECALL stops unexpectedly"
            to "يُنبّهك فقط إذا توقف ريكول فجأة",
        "Weekly nudge if listening is off"
            to "تذكير أسبوعي إذا كان الاستماع متوقفًا",
        "Covers outages + long-off nudge"
            to "يغطي الأعطال + تذكير بعد انقطاع طويل",
        "No alerts — tap to configure"
            to "لا توجد تنبيهات — اضغط للإعداد",
        "CHANGE ▸" to "◂ تغيير",

        // Duration section
        "How far back" to "إلى أي مدى للخلف",

        // Primary / secondary actions
        "RECOVER AUDIO" to "استرجاع الصوت",
        "OR PICK A SPECIFIC TIME RANGE" to "أو اختر فترة زمنية محددة",
        "PLAY LAST RECOVERY" to "تشغيل آخر استرجاع",
        "PAUSE" to "إيقاف مؤقت",
        "RESUME" to "استئناف",
        "SAVE TO DEVICE" to "حفظ على الجهاز",

        // Help link + footer
        "HOW TO USE RECALL" to "كيفية استخدام ريكول",
        "Audio stays on this device.\nNothing is uploaded or shared."
            to "الصوت يبقى على هذا الجهاز.\nلا شيء يتم رفعه أو مشاركته.",

        // Bottom nav
        "HOME" to "الرئيسية",
        "SEARCH" to "البحث",
        "PROFILE" to "الملف الشخصي",

        // Search tab
        "Find a specific moment in your rolling buffer"
            to "ابحث عن لحظة محددة في مخزنك المتجدّد",
        "PICK A TIME RANGE" to "اختر فترة زمنية",
        "Pick a start and end time in your local timezone. RECALL returns exactly that slice from the on-device buffer."
            to "اختر وقت البداية والنهاية حسب منطقتك الزمنية. يُرجع ريكول هذه الشريحة بالضبط من المخزن.",
        "SAVED RECOVERIES" to "الاستردادات المحفوظة",
        "WAV files you've exported via SAVE TO DEVICE appear in your Music folder under RECALL. Open any file manager or music app to browse them — this list view is coming soon."
            to "تظهر ملفات WAV التي صدّرتها عبر \"حفظ على الجهاز\" في مجلد Music داخل RECALL. افتح أي مدير ملفات أو تطبيق موسيقى لتصفّحها — عرض القائمة قادم قريبًا.",

        // Profile tab
        "Account, settings, and quick links" to "الحساب والإعدادات والروابط السريعة",
        "APPEARANCE" to "المظهر",
        "Dark mode" to "الوضع الليلي",
        "Light mode" to "الوضع النهاري",
        "Toggle to switch between dark and light themes"
            to "بدّل بين الوضع الليلي والنهاري",
        "LANGUAGE" to "اللغة",
        "English" to "الإنجليزية",
        "Arabic" to "العربية",
        "Switch between English and Arabic"
            to "بدّل بين الإنجليزية والعربية",
        "How to use RECALL" to "كيفية استخدام ريكول",
        "Buy minutes" to "شراء دقائق",
        "Extend buffer window" to "تمديد نافذة التخزين",
        "Configure alerts" to "إعداد التنبيهات",
        "Privacy policy" to "سياسة الخصوصية",
        "Terms of service" to "شروط الخدمة",

        // Unit abbreviations (used inside formatted numbers)
        "min" to "د",
        "sec" to "ث",
        "hr" to "س",
        "hour" to "ساعة",
        "hours" to "ساعات",
        "day" to "يوم",
        "days" to "أيام",
        "of audio" to "من الصوت",
        "Captured so far" to "تم التسجيل حتى الآن",
        "Free trial" to "تجربة مجانية",
        "Paid balance" to "رصيد مدفوع",
        "sec included" to "ثانية مُدرجة",
        "Trial" to "تجربة",
        "Paid" to "مدفوع",
        "0 seconds" to "٠ ثانية",
        "No audio captured yet. Wait a few seconds after turning listening on."
            to "لم يُسجّل أي صوت بعد. انتظر بضع ثوانٍ بعد تشغيل الاستماع.",

        // Dialog titles / common buttons
        "BUY MINUTES" to "شراء دقائق",
        "EXTEND BUFFER" to "تمديد نافذة التخزين",
        "WELCOME TO RECALL" to "مرحبًا بك في ريكول",
        "TURN RECALL OFF?" to "إيقاف ريكول؟",
        "RECOVER TIME RANGE" to "استرجاع فترة زمنية",
        "START" to "البداية",
        "END" to "النهاية",
        "RECOVER THIS RANGE" to "استرجاع هذه الفترة",
        "Cancel" to "إلغاء",
        "Keep listening" to "الاستمرار بالاستماع",
        "Turn off" to "إيقاف",
        "I AGREE" to "موافق",
        "Exit" to "خروج",
        "DONE" to "تم",
        "CLOSE" to "إغلاق",
        "GOT IT" to "حسنًا",
        "PRIVACY POLICY" to "سياسة الخصوصية",
        "TERMS OF SERVICE" to "شروط الخدمة",
    )
}
