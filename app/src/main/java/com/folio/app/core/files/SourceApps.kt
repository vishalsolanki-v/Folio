package com.folio.app.core.files

/**
 * Friendly names for apps that commonly hand Folio a file (plan §4.3). Hard-coded because Android 11+
 * hides other apps' labels without QUERY_ALL_PACKAGES, which Folio doesn't request.
 */
object SourceApps {
    private val names = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp",
        "com.google.android.gm" to "Gmail",
        "org.telegram.messenger" to "Telegram",
        "org.thunderdog.challegram" to "Telegram",
        "com.google.android.apps.nbu.files" to "Files",
        "com.google.android.documentsui" to "Files",
        "com.android.documentsui" to "Files",
        "com.android.chrome" to "Chrome",
        "com.google.android.apps.docs" to "Drive",
        "com.microsoft.office.outlook" to "Outlook",
        "com.Slack" to "Slack",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.email.provider" to "Samsung Email",
        "com.sec.android.app.myfiles" to "My Files",
        "com.mi.android.globalFileexplorer" to "File Manager",
        "com.microsoft.teams" to "Teams",
        "com.discord" to "Discord",
        "org.thoughtcrime.securesms" to "Signal",
    )

    /** @param referrerHost host of `Activity.referrer` (a package name), or null. */
    fun label(referrerHost: String?): String? = referrerHost?.let(names::get)
}
