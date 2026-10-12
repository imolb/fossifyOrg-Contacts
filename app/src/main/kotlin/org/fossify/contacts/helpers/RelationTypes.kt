package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import ezvcard.VCard
import org.fossify.commons.models.contacts.RelatedContact
import java.util.Locale

// vCard RELATED types supported by the provider mapping
const val RELATED_TYPE_SPOUSE = "spouse"
const val RELATED_TYPE_CHILD = "child"
const val RELATED_TYPE_PARENT = "parent"
const val RELATED_TYPE_BROTHER = "brother"
const val RELATED_TYPE_SISTER = "sister"
const val RELATED_TYPE_FATHER = "father"
const val RELATED_TYPE_MOTHER = "mother"
const val RELATED_TYPE_FRIEND = "friend"
const val RELATED_TYPE_PARTNER = "partner"
const val RELATED_TYPE_DOMESTIC_PARTNER = "domestic-partner"
const val RELATED_TYPE_ASSISTANT = "assistant"
const val RELATED_TYPE_MANAGER = "manager"
const val RELATED_TYPE_REFERRED_BY = "referred by"
const val RELATED_TYPE_RELATIVE = "relative"

// vCard RELATED type to ContactsContract provider type
private val relationTypePairs = listOf(
    RELATED_TYPE_SPOUSE to CommonDataKinds.Relation.TYPE_SPOUSE,
    RELATED_TYPE_CHILD to CommonDataKinds.Relation.TYPE_CHILD,
    RELATED_TYPE_PARENT to CommonDataKinds.Relation.TYPE_PARENT,
    RELATED_TYPE_BROTHER to CommonDataKinds.Relation.TYPE_BROTHER,
    RELATED_TYPE_SISTER to CommonDataKinds.Relation.TYPE_SISTER,
    RELATED_TYPE_FATHER to CommonDataKinds.Relation.TYPE_FATHER,
    RELATED_TYPE_MOTHER to CommonDataKinds.Relation.TYPE_MOTHER,
    RELATED_TYPE_FRIEND to CommonDataKinds.Relation.TYPE_FRIEND,
    RELATED_TYPE_PARTNER to CommonDataKinds.Relation.TYPE_PARTNER,
    RELATED_TYPE_DOMESTIC_PARTNER to CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER,
    RELATED_TYPE_ASSISTANT to CommonDataKinds.Relation.TYPE_ASSISTANT,
    RELATED_TYPE_MANAGER to CommonDataKinds.Relation.TYPE_MANAGER,
    RELATED_TYPE_REFERRED_BY to CommonDataKinds.Relation.TYPE_REFERRED_BY,
    RELATED_TYPE_RELATIVE to CommonDataKinds.Relation.TYPE_RELATIVE,
)

fun getRelationTypeIdAndLabel(typeString: String): Pair<Int, String> {
    val providerType = relationTypePairs.firstOrNull {
        it.first == typeString.lowercase(Locale.ROOT)
    }?.second ?: CommonDataKinds.Relation.TYPE_CUSTOM

    val label = if (providerType == CommonDataKinds.Relation.TYPE_CUSTOM) {
        typeString
    } else {
        ""
    }

    return providerType to label
}

fun getRelationTypeValue(type: Int, label: String) = relationTypePairs
    .firstOrNull { it.second == type }?.first ?: label

// Apple extension properties carrying relations, e.g.
// "item2.X-ABRELATEDNAMES:Sophia" + "item2.X-ABLabel:_$!<Child>!$_"
const val X_ABRELATED_NAMES = "X-ABRELATEDNAMES"
const val X_AB_LABEL = "X-ABLabel"

// Apple wraps standard labels in "_$!<...>!$_", custom labels are plain text
fun unwrapAppleLabel(label: String): String {
    val trimmed = label.trim()
    return if (trimmed.startsWith("_\$!<") && trimmed.endsWith(">!\$_")) {
        trimmed.removePrefix("_\$!<").removeSuffix(">!\$_")
    } else {
        trimmed
    }
}

fun getAppleRelatedContacts(ezContact: VCard): List<RelatedContact> {
    val labels = ezContact.getExtendedProperties(X_AB_LABEL).associateBy { it.group }
    return ezContact.getExtendedProperties(X_ABRELATED_NAMES).mapNotNull { related ->
        val name = related.value?.trim().orEmpty()
        if (name.isEmpty()) {
            return@mapNotNull null
        }

        val typeString = labels[related.group]?.value?.let { unwrapAppleLabel(it) }.orEmpty()
        val (type, label) = getRelationTypeIdAndLabel(typeString)
        RelatedContact(name, type, label)
    }
}
