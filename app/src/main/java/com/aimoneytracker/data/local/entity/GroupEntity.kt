package com.aimoneytracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aimoneytracker.domain.model.GroupType

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GroupType = GroupType.OTHER,
    val photoUri: String? = null,
    val memberPersonIds: List<Long> = emptyList(),
    val createdAt: Long = 0,
)
