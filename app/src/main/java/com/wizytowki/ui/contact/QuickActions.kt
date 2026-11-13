package com.wizytowki.ui.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Quick actions of the person card (REQUIREMENTS 8a, screen 7): call, e-mail, map, website.
 * Each opens the phone's own app; nothing is sent by this app (WNF-01).
 */
object QuickActions {
    /** Opens the dialer with the number filled in – the user still confirms the call. */
    fun call(phone: String) = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))

    fun email(address: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address.trim()))

    /** Map search for the address (geo URI understood by Google Maps and other map apps). */
    fun map(address: String) = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))

    fun web(url: String): Intent {
        val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return Intent(Intent.ACTION_VIEW, Uri.parse(full))
    }

    /** Starts [intent]; false when the phone has no app for it. */
    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
