package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Related
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.fossify.commons.models.contacts.RelatedContact
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

/**
 * Mirrors the way [VcfImporter] parses and [VcfExporter] writes RELATED properties,
 * but without Android dependencies, to verify the vCard round-trip is loss-free.
 */
class RelatedVcfTest {

    private fun writeCard(relatedContacts: List<RelatedContact>): String {
        val card = VCard()
        card.setFormattedName("Test Contact")
        relatedContacts.forEach { relatedContact ->
            val relatedProperty = Related(relatedContact.name)
            val typeLabel = getRelationTypeValue(relatedContact.type, relatedContact.label)
            if (typeLabel.isNotEmpty()) {
                relatedProperty.parameters.addType(typeLabel)
            }
            card.addRelated(relatedProperty)
        }
        val writer = StringWriter()
        Ezvcard.write(card).version(VCardVersion.V4_0).go(writer)
        return writer.toString()
    }

    private fun parseCard(vcard: String): List<RelatedContact> {
        val card = Ezvcard.parse(StringReader(vcard)).first()
        val relatedContacts = ArrayList<RelatedContact>()
        card.relations.forEach { related ->
            val name = related.text ?: related.uri ?: return@forEach
            if (name.isNotEmpty()) {
                // TYPE is optional in vCard 4.0, so keep unlabeled relations as custom
                val typeString = related.types.firstOrNull()?.value ?: ""
                val (type, label) = getRelationTypeIdAndLabel(typeString)
                relatedContacts.add(RelatedContact(name, type, label))
            }
        }
        return relatedContacts
    }

    @Test
    fun samsungStyleRelationsAreParsed() {
        val vcard = "BEGIN:VCARD\n" +
            "VERSION:4.0\n" +
            "FN:Max Mustermann\n" +
            "RELATED;TYPE=child:Peter\n" +
            "RELATED;TYPE=spouse:Anna\n" +
            "END:VCARD\n"

        val relatedContacts = parseCard(vcard)
        assertEquals(2, relatedContacts.size)
        assertEquals(RelatedContact("Peter", CommonDataKinds.Relation.TYPE_CHILD, ""), relatedContacts[0])
        assertEquals(RelatedContact("Anna", CommonDataKinds.Relation.TYPE_SPOUSE, ""), relatedContacts[1])
    }

    @Test
    fun relatedWithoutTypeIsParsedAsCustom() {
        val vcard = "BEGIN:VCARD\n" +
            "VERSION:4.0\n" +
            "FN:Max Mustermann\n" +
            "RELATED:Peter\n" +
            "END:VCARD\n"

        val relatedContacts = parseCard(vcard)
        assertEquals(listOf(RelatedContact("Peter", CommonDataKinds.Relation.TYPE_CUSTOM, "")), relatedContacts)
    }

    @Test
    fun unlabeledRelationRoundTripsLossFree() {
        val original = listOf(RelatedContact("Peter", CommonDataKinds.Relation.TYPE_CUSTOM, ""))

        val written = writeCard(original)
        assertTrue("unlabeled relations must be written without a TYPE parameter", written.contains("RELATED:Peter"))
        assertEquals(original, parseCard(written))
    }

    @Test
    fun relationsAreExportedAsPlainText() {
        val output = writeCard(listOf(RelatedContact("Peter", CommonDataKinds.Relation.TYPE_CHILD, "")))
        assertTrue("exported card must contain RELATED;TYPE=child:Peter", output.contains("RELATED;TYPE=child:Peter"))
    }

    @Test
    fun roundTripPreservesKnownTypes() {
        val original = listOf(
            RelatedContact("Peter", CommonDataKinds.Relation.TYPE_CHILD, ""),
            RelatedContact("Anna", CommonDataKinds.Relation.TYPE_SPOUSE, ""),
            RelatedContact("Bob", CommonDataKinds.Relation.TYPE_PARENT, ""),
        )

        val reparsed = parseCard(writeCard(original))
        assertEquals(original, reparsed)
    }

    @Test
    fun roundTripPreservesCustomTypes() {
        val original = listOf(
            RelatedContact("Bob", CommonDataKinds.Relation.TYPE_CUSTOM, "colleague"),
            RelatedContact("Alice", CommonDataKinds.Relation.TYPE_CUSTOM, "sibling"),
        )

        val written = writeCard(original)
        val reparsed = parseCard(written)
        assertEquals(original, reparsed)
    }

    @Test
    fun roundTripOfImportedSamsungCardIsLossFree() {
        val samsungVcard = "BEGIN:VCARD\n" +
            "VERSION:4.0\n" +
            "FN:Max Mustermann\n" +
            "RELATED;TYPE=child:Peter\n" +
            "RELATED;TYPE=spouse:Anna\n" +
            "END:VCARD\n"

        val imported = parseCard(samsungVcard)
        val reimported = parseCard(writeCard(imported))
        assertEquals(imported, reimported)
    }

    @Test
    fun appleStyleRelationsAreParsed() {
        val appleVcard = "BEGIN:VCARD\n" +
            "VERSION:3.0\n" +
            "FN:Michael Kahms\n" +
            "item2.X-ABRELATEDNAMES:Sophia\n" +
            "item2.X-ABLabel:_\$!<Child>!\$_\n" +
            "item3.X-ABRELATEDNAMES:Valentin\n" +
            "item3.X-ABLabel:_\$!<Child>!\$_\n" +
            "item4.X-ABRELATEDNAMES:Kerstin \n" +
            "item4.X-ABLabel:_\$!<Partner>!\$_\n" +
            "END:VCARD\n"

        val card = Ezvcard.parse(StringReader(appleVcard)).first()
        val relatedContacts = getAppleRelatedContacts(card)
        assertEquals(3, relatedContacts.size)
        assertEquals(RelatedContact("Sophia", CommonDataKinds.Relation.TYPE_CHILD, ""), relatedContacts[0])
        assertEquals(RelatedContact("Valentin", CommonDataKinds.Relation.TYPE_CHILD, ""), relatedContacts[1])
        assertEquals("trailing whitespace must be trimmed", RelatedContact("Kerstin", CommonDataKinds.Relation.TYPE_PARTNER, ""), relatedContacts[2])
    }

    @Test
    fun appleCustomLabelIsPreserved() {
        val appleVcard = "BEGIN:VCARD\n" +
            "VERSION:3.0\n" +
            "FN:Test\n" +
            "item2.X-ABRELATEDNAMES:Moritz\n" +
            "item2.X-ABLabel:Patenkind\n" +
            "END:VCARD\n"

        val card = Ezvcard.parse(StringReader(appleVcard)).first()
        val relatedContacts = getAppleRelatedContacts(card)
        assertEquals(listOf(RelatedContact("Moritz", CommonDataKinds.Relation.TYPE_CUSTOM, "Patenkind")), relatedContacts)
    }

    @Test
    fun appleRelationWithoutLabelIsCustom() {
        val appleVcard = "BEGIN:VCARD\n" +
            "VERSION:3.0\n" +
            "FN:Test\n" +
            "item2.X-ABRELATEDNAMES:Norman\n" +
            "END:VCARD\n"

        val card = Ezvcard.parse(StringReader(appleVcard)).first()
        val relatedContacts = getAppleRelatedContacts(card)
        assertEquals(listOf(RelatedContact("Norman", CommonDataKinds.Relation.TYPE_CUSTOM, "")), relatedContacts)
    }

    @Test
    fun appleLabelIsUnwrapped() {
        assertEquals("Child", unwrapAppleLabel("_\$!<Child>!\$_"))
        assertEquals("Child", unwrapAppleLabel(" _\$!<Child>!\$_ "))
        assertEquals("Patenkind", unwrapAppleLabel("Patenkind"))
    }
}
