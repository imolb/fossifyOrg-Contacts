package org.fossify.contacts.helpers

import android.provider.ContactsContract.CommonDataKinds
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationTypesTest {

    @Test
    fun knownVcardTypesMapToProviderTypes() {
        val expected = mapOf(
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

        expected.forEach { (vcardType, providerType) ->
            val (type, label) = getRelationTypeIdAndLabel(vcardType)
            assertEquals("vCard type $vcardType should map to $providerType", providerType, type)
            assertEquals("known types must not set a label", "", label)
        }
    }

    @Test
    fun unknownVcardTypesMapToCustomWithLabel() {
        listOf("colleague", "sibling", "kin", "BestFriend", "emergency").forEach { vcardType ->
            val (type, label) = getRelationTypeIdAndLabel(vcardType)
            assertEquals(CommonDataKinds.Relation.TYPE_CUSTOM, type)
            assertEquals("unknown type $vcardType must be preserved as the label", vcardType, label)
        }
    }

    @Test
    fun providerTypesMapBackToVcardTypes() {
        val expected = mapOf(
            CommonDataKinds.Relation.TYPE_SPOUSE to RELATED_TYPE_SPOUSE,
            CommonDataKinds.Relation.TYPE_CHILD to RELATED_TYPE_CHILD,
            CommonDataKinds.Relation.TYPE_PARENT to RELATED_TYPE_PARENT,
            CommonDataKinds.Relation.TYPE_BROTHER to RELATED_TYPE_BROTHER,
            CommonDataKinds.Relation.TYPE_SISTER to RELATED_TYPE_SISTER,
            CommonDataKinds.Relation.TYPE_FATHER to RELATED_TYPE_FATHER,
            CommonDataKinds.Relation.TYPE_MOTHER to RELATED_TYPE_MOTHER,
            CommonDataKinds.Relation.TYPE_FRIEND to RELATED_TYPE_FRIEND,
            CommonDataKinds.Relation.TYPE_PARTNER to RELATED_TYPE_PARTNER,
            CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER to RELATED_TYPE_DOMESTIC_PARTNER,
            CommonDataKinds.Relation.TYPE_ASSISTANT to RELATED_TYPE_ASSISTANT,
            CommonDataKinds.Relation.TYPE_MANAGER to RELATED_TYPE_MANAGER,
            CommonDataKinds.Relation.TYPE_REFERRED_BY to RELATED_TYPE_REFERRED_BY,
            CommonDataKinds.Relation.TYPE_RELATIVE to RELATED_TYPE_RELATIVE,
        )

        expected.forEach { (providerType, vcardType) ->
            assertEquals(vcardType, getRelationTypeValue(providerType, ""))
        }
    }

    @Test
    fun customTypeExportsItsLabel() {
        assertEquals("colleague", getRelationTypeValue(CommonDataKinds.Relation.TYPE_CUSTOM, "colleague"))
    }

    @Test
    fun typeMappingRoundTripsThroughProviderTypes() {
        listOf(
            RELATED_TYPE_SPOUSE,
            RELATED_TYPE_CHILD,
            RELATED_TYPE_PARENT,
            RELATED_TYPE_FRIEND,
            RELATED_TYPE_MANAGER,
        ).forEach { vcardType ->
            val (type, label) = getRelationTypeIdAndLabel(vcardType)
            assertEquals("round trip of $vcardType must be loss-free", vcardType, getRelationTypeValue(type, label))
        }
    }

    @Test
    fun unknownTypeRoundTripsAsCustomLabel() {
        val (type, label) = getRelationTypeIdAndLabel("colleague")
        assertEquals("colleague", getRelationTypeValue(type, label))
    }
}
