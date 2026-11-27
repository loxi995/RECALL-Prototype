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

        // Buffer card subtext
        "Custom window · tap to change" to "نافذة مخصّصة · اضغط للتغيير",

        // Buffer tier picker
        "How far back should RECALL keep audio on this device? Demo mode — tapping a tier unlocks it instantly."
            to "إلى أي مدى يحتفظ ريكول بالصوت على جهازك؟ الوضع التجريبي — اضغط على فئة ليتم فتحها فورًا.",
        "24 hours" to "٢٤ ساعة",
        "2 days" to "يومان",
        "3 days" to "٣ أيام",
        "7 days" to "٧ أيام",
        "Included" to "مضمنة",
        "DEFAULT" to "افتراضي",
        "+1 day" to "+يوم واحد",
        "+1 DAY" to "+يوم واحد",
        "MOST POPULAR" to "الأكثر شعبية",
        "POWER USER" to "للمحترفين",
        "ACTIVE" to "مفعّل",

        // Alerts picker body + row descriptions
        "RECALL only notifies you when something actually goes wrong — never 'come back and use the app' nags."
            to "يُنبّهك ريكول فقط عند حدوث خطأ فعلي — لا رسائل \"عُد لاستخدام التطبيق\" مزعجة.",
        "Watchdog alert" to "تنبيه المراقبة",
        "Notify me immediately if RECALL stops listening (OS killed the service, background limit hit, etc.)."
            to "أخبرني فورًا إذا توقف ريكول عن الاستماع (أُغلقت الخدمة من النظام أو تم تجاوز حد الخلفية، إلخ).",
        "If I've had RECALL off for 7 days, nudge me once to turn it back on."
            to "إذا بقي ريكول متوقفًا 7 أيام، ذكّرني مرة لإعادة تشغيله.",

        // Duration picker (stock AlertDialog)
        "How far back?" to "إلى أي مدى للخلف؟",
        "Minutes" to "الدقائق",
        "Seconds" to "الثواني",
        "Set" to "ضبط",
        "Cancel" to "إلغاء",

        // Language picker
        "CHANGE ▸" to "◂ تغيير",
        "Change language" to "تغيير اللغة",
        "Pick your preferred language" to "اختر لغتك المفضّلة",

        // HOW TO USE intro + step titles + bodies
        "RECALL is an audio time machine. It quietly keeps a rolling buffer of ambient sound so you can go back and grab anything you missed."
            to "ريكول آلة زمن صوتية. يحتفظ بمخزن متجدد من الصوت المحيط بهدوء حتى تتمكن من العودة والتقاط ما فاتك.",
        "TURN ON LISTENING" to "شغّل الاستماع",
        "Toggle the Always Listening switch to ON. RECALL starts quietly capturing audio in the background. The green dot in the status bar is an Android privacy indicator — not something RECALL controls."
            to "فعّل مفتاح \"الاستماع الدائم\". يبدأ ريكول بتسجيل الصوت بهدوء في الخلفية. النقطة الخضراء في شريط الحالة مؤشر خصوصية من أندرويد — ليست شيئًا يتحكم به ريكول.",
        "LET IT CAPTURE" to "دعه يسجّل",
        "Keep the app running in the background. You can lock your phone, open other apps — RECALL keeps listening. 'Captured so far' shows how much audio is in the buffer."
            to "اترك التطبيق يعمل في الخلفية. يمكنك قفل الهاتف أو فتح تطبيقات أخرى — يستمر ريكول في الاستماع. يُظهر \"تم التسجيل حتى الآن\" حجم الصوت في المخزن.",
        "PICK HOW FAR BACK" to "اختر مدى العودة",
        "Tap 'How far back' and spin the wheels to choose a duration (30 seconds up to your buffer limit). You can also tap a number to type it directly."
            to "اضغط على \"إلى أي مدى للخلف\" ودوّر العجلات لاختيار المدة (من 30 ثانية حتى حد المخزن). يمكنك أيضًا الضغط على رقم لإدخاله يدويًا.",
        "RECOVER THE AUDIO" to "استرجع الصوت",
        "Tap RECOVER AUDIO. RECALL grabs that slice from the buffer, decodes it, and makes it ready to play."
            to "اضغط \"استرجاع الصوت\". يأخذ ريكول هذه الشريحة من المخزن ويفكّ ترميزها ويجهّزها للتشغيل.",
        "PLAY & SAVE" to "تشغيل وحفظ",
        "Tap PLAY LAST RECOVERY to listen. Tap SAVE TO DEVICE to write it as a WAV file in your Music/RECALL folder — permanent and shareable."
            to "اضغط \"تشغيل آخر استرجاع\" للاستماع. اضغط \"حفظ على الجهاز\" لحفظه كملف WAV في مجلد Music/RECALL — دائم ويمكن مشاركته.",
        "Each recovery costs minutes from your balance. New users get a 60-second free trial. Tap +BUY MIN at the top-right to add more."
            to "كل استرجاع يكلف دقائق من رصيدك. يحصل المستخدمون الجدد على تجربة مجانية مدتها 60 ثانية. اضغط +شراء دقائق في الأعلى لإضافة المزيد.",
        "EXTEND YOUR BUFFER" to "مدّد نافذة التخزين",
        "Default is 24 hours of audio kept on-device. Tap the BUFFER WINDOW card to extend up to 7 days (optional upgrade)."
            to "الافتراضي 24 ساعة من الصوت على الجهاز. اضغط بطاقة \"نافذة التخزين\" للتمديد حتى 7 أيام (ترقية اختيارية).",
        "TIPS" to "نصائح",
        "• Add RECALL to your battery-optimization whitelist so Samsung doesn't kill the service in your pocket.\n• Audio never leaves your phone. Uninstalling the app deletes every byte instantly.\n• You are responsible for recording laws in your region — don't use RECALL on people who haven't consented."
            to "• أضف ريكول إلى قائمة استثناء تحسين البطارية حتى لا يوقف سامسونج الخدمة أثناء وجود الهاتف في جيبك.\n• الصوت لا يغادر هاتفك أبدًا. إلغاء تثبيت التطبيق يحذف كل بايت فورًا.\n• أنت مسؤول عن قوانين التسجيل في منطقتك — لا تستخدم ريكول على أشخاص لم يوافقوا.",

        // Pack picker body + labels
        "Demo mode — tapping a pack simulates a purchase and credits your balance instantly. Google Play Billing will be wired in before public launch."
            to "الوضع التجريبي — اضغط على عرض ليتم محاكاة الشراء وإضافة الرصيد فورًا. سيتم ربط Google Play Billing قبل الإطلاق الرسمي.",
        "minutes" to "دقيقة",
        "STARTER" to "مبتدئ",
        "SAVE 16%" to "وفّر ١٦٪",
        "BEST VALUE" to "أفضل قيمة",
        "SAVE 50%" to "وفّر ٥٠٪",
        "BEST DEAL" to "أفضل صفقة",
        "+N minutes added (demo)." to "تمت إضافة الدقائق (تجريبي).",

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
