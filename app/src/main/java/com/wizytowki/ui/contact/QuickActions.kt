package com.wizytowki.ui.contact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import com.wizytowki.domain.ContactDraft

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

    /**
     * The phone's "new contact" form filled with this contact (#122). The user saves it there, so
     * the app needs no contacts permission (WNF-04); every phone, e-mail and website is passed.
     */
    fun addToPhoneContacts(contact: ContactDraft): Intent {
        val name = listOf(contact.firstName, contact.lastName).filter { it.isNotBlank() }.joinToString(" ")
        val rows = ArrayList<ContentValues>()
        contact.phones.forEach { rows += row(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Phone.NUMBER to it, CommonDataKinds.Phone.TYPE to CommonDataKinds.Phone.TYPE_WORK) }
        contact.emails.forEach { rows += row(CommonDataKinds.Email.CONTENT_ITEM_TYPE, CommonDataKinds.Email.ADDRESS to it, CommonDataKinds.Email.TYPE to CommonDataKinds.Email.TYPE_WORK) }
        (contact.websites + contact.social).forEach { rows += row(CommonDataKinds.Website.CONTENT_ITEM_TYPE, CommonDataKinds.Website.URL to it, CommonDataKinds.Website.TYPE to CommonDataKinds.Website.TYPE_WORK) }
        val position = contact.positionPl.ifBlank { contact.positionEn }
        val department = contact.departmentPl.ifBlank { contact.departmentEn }
        if (contact.company.isNotBlank() || position.isNotBlank()) {
            rows += row(
                CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                CommonDataKinds.Organization.COMPANY to contact.company,
                CommonDataKinds.Organization.TITLE to position,
                CommonDataKinds.Organization.DEPARTMENT to department,
            )
        }
        contact.addressPl.ifBlank { contact.addressEn }.takeIf { it.isNotBlank() }?.let {
            rows += row(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS to it, CommonDataKinds.StructuredPostal.TYPE to CommonDataKinds.StructuredPostal.TYPE_WORK)
        }
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
            if (contact.comment.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NOTES, contact.comment)
            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
        }
    }

    private fun row(mime: String, vararg values: Pair<String, Any>) = ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, mime)
        values.forEach { (key, value) ->
            when (value) {
                is Int -> put(key, value)
                else -> put(key, value.toString())
            }
        }
    }

    /** Starts [intent]; false when the phone has no app for it. */
    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
