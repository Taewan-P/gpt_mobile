package dev.chungjungsoo.gptmobile.data.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_graph_entities",
    indices = [
        Index(value = ["scope", "normalizedName"], unique = true),
        Index(value = ["entityType"]),
        Index(value = ["sourceChatId"])
    ]
)
data class MemoryGraphEntityRecord(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val entityType: String,
    val scope: String = "personal",
    val standalone: Boolean = false,
    val sourceChatId: Int = 0,
    val sourceMessageId: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memory_graph_observations",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGraphEntityRecord::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entityId"), Index("sourceChatId"), Index("sourceKind")]
)
data class MemoryGraphObservationRecord(
    @PrimaryKey val id: String,
    val entityId: String,
    val observation: String,
    val normalizedObservation: String,
    val scope: String = "personal",
    val sourceKind: String = "vault",
    val sourceChatId: Int = 0,
    val sourceMessageId: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memory_graph_relations",
    foreignKeys = [
        ForeignKey(
            entity = MemoryGraphEntityRecord::class,
            parentColumns = ["id"],
            childColumns = ["fromEntityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryGraphEntityRecord::class,
            parentColumns = ["id"],
            childColumns = ["toEntityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("fromEntityId"),
        Index("toEntityId"),
        Index("sourceChatId"),
        Index("sourceKind"),
        Index(value = ["fromEntityId", "toEntityId", "relationType", "sourceKind"], unique = true)
    ]
)
data class MemoryGraphRelationRecord(
    @PrimaryKey val id: String,
    val fromEntityId: String,
    val toEntityId: String,
    val relationType: String,
    val scope: String = "personal",
    val sourceKind: String = "vault",
    val sourceChatId: Int = 0,
    val sourceMessageId: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class MemoryGraphRelationView(
    val from: String,
    val to: String,
    val relationType: String
)

data class MemoryGraphNode(
    val id: String,
    val name: String,
    val entityType: String,
    val observations: List<String>,
    val relations: List<MemoryGraphRelationView>
)

data class MemoryGraphEntityInput(
    val name: String,
    val entityType: String = "ENTITY"
)
