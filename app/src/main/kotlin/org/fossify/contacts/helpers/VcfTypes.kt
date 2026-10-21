package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import ezvcard.VCard
import java.util.Locale

// Apple extension property carrying anniversaries in vCard 3.0 (used by DAVx5)
const val X_ANNIVERSARY = "X-ANNIVERSARY"

// vCard TYPE values that describe format or preference, not the location
private val TYPE_INDICATORS = setOf("internet", "pref", "voice", "x400")

fun getAppleLabels(ezContact: VCard): Map<String, String> =
    ezContact.getExtendedProperties(X_AB_LABEL).associate { property ->
        (property.group ?: "") to unwrapAppleLabel(property.value ?: "")
    }

/**
 * Resolves a property's TYPE parameter list to a provider type + custom label.
 * A non-empty Apple X-ABLabel overrides everything. Otherwise the first
 * recognized location type wins; unknown values become the custom label.
 */
private fun resolveTypeAndLabel(
    types: List<String>,
    abLabel: String,
    recognized: (String) -> Int?,
    defaultType: Int
): Pair<Int, String> {
    if (abLabel.isNotEmpty()) {
        return CommonDataKinds.BaseTypes.TYPE_CUSTOM to abLabel
    }

    val usable = types.map { it.lowercase(Locale.ROOT) }.filter { it !in TYPE_INDICATORS }
    if (usable.isEmpty()) {
        return defaultType to ""
    }

    usable.forEach { value ->
        val type = recognized(value)
        if (type != null) {
            return type to ""
        }
    }

    return CommonDataKinds.BaseTypes.TYPE_CUSTOM to usable.first()
}

fun getResolvedEmailTypeAndLabel(types: List<String>, abLabel: String): Pair<Int, String> =
    resolveTypeAndLabel(
        types = types,
        abLabel = abLabel,
        recognized = { value ->
            when (value) {
                "home" -> Email.TYPE_HOME
                "work" -> Email.TYPE_WORK
                "mobile" -> Email.TYPE_MOBILE
                "other", "x-other" -> Email.TYPE_OTHER
                else -> null
            }
        },
        defaultType = Email.TYPE_HOME
    )

fun getResolvedAddressTypeAndLabel(types: List<String>, abLabel: String): Pair<Int, String> =
    resolveTypeAndLabel(
        types = types,
        abLabel = abLabel,
        recognized = { value ->
            when (value) {
                "home" -> StructuredPostal.TYPE_HOME
                "work" -> StructuredPostal.TYPE_WORK
                "other", "x-other" -> StructuredPostal.TYPE_OTHER
                else -> null
            }
        },
        defaultType = StructuredPostal.TYPE_HOME
    )

fun getResolvedPhoneNumberTypeAndLabel(types: List<String>, abLabel: String): Pair<Int, String> {
    if (abLabel.isNotEmpty()) {
        return CommonDataKinds.BaseTypes.TYPE_CUSTOM to abLabel
    }

    val usable = types.map { it.lowercase(Locale.ROOT) }.filter { it !in TYPE_INDICATORS }
    if (usable.isEmpty()) {
        return Phone.TYPE_MOBILE to ""
    }

    // fax is a subtype: the location type decides between home and work fax
    if (usable.contains("fax")) {
        return when {
            usable.contains("home") -> Phone.TYPE_FAX_HOME
            else -> Phone.TYPE_FAX_WORK
        } to ""
    }

    usable.forEach { value ->
        val type = when (value) {
            "cell" -> Phone.TYPE_MOBILE
            "home" -> Phone.TYPE_HOME
            "work" -> Phone.TYPE_WORK
            "main" -> Phone.TYPE_MAIN
            "pager" -> Phone.TYPE_PAGER
            "other", "x-other" -> Phone.TYPE_OTHER
            else -> null
        }
        if (type != null) {
            return type to ""
        }
    }

    return CommonDataKinds.BaseTypes.TYPE_CUSTOM to usable.first()
}

// parses an X-ANNIVERSARY value into the app's event day code ("yyyy-MM-dd" or "--MM-dd")
fun parseXAnniversaryDate(value: String): String? {
    val trimmed = value.trim()
    val date = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")
    val partialDate = Regex("""^--(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")
    return when {
        date.matches(trimmed) -> trimmed
        partialDate.matches(trimmed) -> trimmed
        else -> null
    }
}
