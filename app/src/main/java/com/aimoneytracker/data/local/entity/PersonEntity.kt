package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.RelationshipType

@Entity(tableName = "people", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String? = null,
    val photoUri: String? = null,
    val relationship: RelationshipType = RelationshipType.OTHER,
    val groupId: Long? = null,
    val contactLookupKey: String? = null,
    val phone: String? = null,
    val notes: String? = null,
    val isMerchant: Boolean = false,
    val createdAt: Long = 0,
)

/**
 * Maps one UPI handle / VPA (e.g. "rahul.k@okaxis") to a person, so multiple handles can collapse
 * onto a single person. Used by §7 learning and §8 auto-linking.
 */
@Entity(
    tableName = "person_handles",
    indices = [Index(value = ["handle"], unique = true), Index("personId")]
)
data class PersonHandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val handle: String,          // normalized lowercase vpa or phone
    val displayName: String? = null,
    val seenCount: Int = 1,
    val lastSeen: Long = 0,
)
