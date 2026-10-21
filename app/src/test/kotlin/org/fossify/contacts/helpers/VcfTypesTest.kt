package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import ezvcard.Ezvcard
import org.fossify.commons.models.contacts.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

/**
 * Verifies the vCard TYPE-list resolution and X-ABLabel handling used by
 * [VcfImporter], plus the X-ANNIVERSARY fallback and ORG department mapping.
 */
class VcfTypesTest {

    // ---------- email ----------

    @Test
    fun internetIndicatorIsSkippedOnEmails() {
        val (type, label) = getResolvedEmailTypeAndLabel(listOf("internet", "work"), "")
        assertEquals(CommonDataKinds.Email.TYPE_WORK, type)
        assertEquals("", label)
    }

    @Test
    fun xOtherEmailsImportAsOther() {
        val (type, label) = getResolvedEmailTypeAndLabel(listOf("x-other"), "")
        assertEquals(CommonDataKinds.Email.TYPE_OTHER, type)
        assertEquals("", label)
    }

    @Test
    fun emailWithoutUsableTypeDefaultsToHome() {
        val (type, label) = getResolvedEmailTypeAndLabel(listOf("internet"), "")
        assertEquals(CommonDataKinds.Email.TYPE_HOME, type)
        assertEquals("", label)
    }

    @Test
    fun unknownEmailTypeBecomesCustomLabel() {
        val (type, label) = getResolvedEmailTypeAndLabel(listOf("internet", "school"), "")
        assertEquals(CommonDataKinds.Email.TYPE_CUSTOM, type)
        assertEquals("school", label)
    }

    // ---------- phone ----------

    @Test
    fun voiceIndicatorIsSkippedOnPhones() {
        val (type, label) = getResolvedPhoneNumberTypeAndLabel(listOf("voice", "cell"), "")
        assertEquals(CommonDataKinds.Phone.TYPE_MOBILE, type)
        assertEquals("", label)
    }

    @Test
    fun faxIsResolvedFromAnyPosition() {
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_HOME, getResolvedPhoneNumberTypeAndLabel(listOf("home", "fax"), "").first)
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_HOME, getResolvedPhoneNumberTypeAndLabel(listOf("fax", "home"), "").first)
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_WORK, getResolvedPhoneNumberTypeAndLabel(listOf("fax"), "").first)
        assertEquals(CommonDataKinds.Phone.TYPE_FAX_WORK, getResolvedPhoneNumberTypeAndLabel(listOf("work", "pref", "fax"), "").first)
    }

    @Test
    fun phoneWithoutUsableTypeDefaultsToMobile() {
        val (type, _) = getResolvedPhoneNumberTypeAndLabel(emptyList(), "")
        assertEquals(CommonDataKinds.Phone.TYPE_MOBILE, type)
    }

    // ---------- address ----------

    @Test
    fun addressTypesAreResolved() {
        assertEquals(CommonDataKinds.StructuredPostal.TYPE_WORK, getResolvedAddressTypeAndLabel(listOf("internet", "work"), "").first)
        assertEquals(CommonDataKinds.StructuredPostal.TYPE_OTHER, getResolvedAddressTypeAndLabel(listOf("x-other"), "").first)
        val (type, label) = getResolvedAddressTypeAndLabel(listOf("university"), "")
        assertEquals(CommonDataKinds.StructuredPostal.TYPE_CUSTOM, type)
        assertEquals("university", label)
    }

    // ---------- X-ABLabel override ----------

    @Test
    fun nonEmptyAbLabelOverridesTypeParams() {
        val (type, label) = getResolvedAddressTypeAndLabel(listOf("x-other"), "University")
        assertEquals(CommonDataKinds.StructuredPostal.TYPE_CUSTOM, type)
        assertEquals("University", label)
    }

    @Test
    fun localizedLabelsStayCustom() {
        val (type, label) = getResolvedPhoneNumberTypeAndLabel(emptyList(), "Mobil")
        assertEquals(CommonDataKinds.Phone.TYPE_CUSTOM, type)
        assertEquals("Mobil", label)
    }

    @Test
    fun emptyAbLabelFallsBackToTypeParams() {
        val (type, label) = getResolvedEmailTypeAndLabel(listOf("internet", "work"), "")
        assertEquals(CommonDataKinds.Email.TYPE_WORK, type)
        assertEquals("", label)
    }

    @Test
    fun wrappedAppleLabelsAreUnwrapped() {
        val card = Ezvcard.parse(
            StringReader(
                "BEGIN:VCARD\nVERSION:3.0\nFN:Test\n" +
                    "item1.X-ABRELATEDNAMES:Anna\nitem1.X-ABLabel:_\$!<Spouse>!\$_\nEND:VCARD\n"
            )
        ).first()
        val labels = getAppleLabels(card)
        assertEquals("Spouse", labels["item1"])
    }

    // ---------- X-ANNIVERSARY ----------

    @Test
    fun xAnniversaryValuesAreParsed() {
        assertEquals("1976-01-24", parseXAnniversaryDate("1976-01-24"))
        assertEquals("--05-01", parseXAnniversaryDate("--05-01"))
        assertNull(parseXAnniversaryDate("yesterday"))
        assertNull(parseXAnniversaryDate("1976-13-45"))
    }

    @Test
    fun xAnniversaryIsUsedWhenAnniversaryIsMissing() {
        val card = Ezvcard.parse(
            StringReader(
                "BEGIN:VCARD\nVERSION:3.0\nFN:Ute Langhof\nBDAY:1976-01-24\nX-ANNIVERSARY:2010-06-12\nEND:VCARD\n"
            )
        ).first()

        val events = ArrayList<Event>()
        card.birthdays.forEach { events.add(Event(it.date.toString(), CommonDataKinds.Event.TYPE_BIRTHDAY)) }
        // mirrors VcfImporter: the fallback only runs when no ANNIVERSARY exists
        if (card.anniversaries.isEmpty()) {
            card.getExtendedProperties(X_ANNIVERSARY).forEach { raw ->
                parseXAnniversaryDate(raw.value)?.let {
                    events.add(Event(it, CommonDataKinds.Event.TYPE_ANNIVERSARY))
                }
            }
        }

        assertEquals(2, events.size)
        assertEquals(CommonDataKinds.Event.TYPE_BIRTHDAY, events[0].type)
        assertEquals(CommonDataKinds.Event.TYPE_ANNIVERSARY, events[1].type)
        assertEquals("2010-06-12", events[1].value)
    }

    // ---------- ORG department ----------

    @Test
    fun orgSecondaryValueIsDepartment() {
        val card = Ezvcard.parse(
            StringReader("BEGIN:VCARD\nVERSION:3.0\nFN:Test\nORG:Acme;Research\nEND:VCARD\n")
        ).first()
        assertEquals("Acme", card.organization?.values?.firstOrNull())
        assertEquals("Research", card.organization?.values?.getOrNull(1))
    }

    @Test
    fun escapedSemicolonStaysOneOrgValue() {
        val card = Ezvcard.parse(
            StringReader("BEGIN:VCARD\nVERSION:3.0\nFN:Test\nORG:BMW\\; ehemals ALTRAN\nEND:VCARD\n")
        ).first()
        assertEquals("BMW; ehemals ALTRAN", card.organization?.values?.firstOrNull())
        assertNull(card.organization?.values?.getOrNull(1))
    }
}
