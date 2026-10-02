package dev.chungjungsoo.gptmobile.data.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MemoryGraphDao {
    @Upsert suspend fun upsertEntities(entities: List<MemoryGraphEntityRecord>)
    @Upsert suspend fun upsertObservations(observations: List<MemoryGraphObservationRecord>)
    @Upsert suspend fun upsertRelations(relations: List<MemoryGraphRelationRecord>)

    @Query("SELECT * FROM memory_graph_entities WHERE id = :id")
    suspend fun entityById(id: String): MemoryGraphEntityRecord?

    @Query("SELECT * FROM memory_graph_entities WHERE scope = :scope AND normalizedName = :normalizedName LIMIT 1")
    suspend fun entityByName(scope: String, normalizedName: String): MemoryGraphEntityRecord?

    @Query("SELECT * FROM memory_graph_entities WHERE scope = :scope ORDER BY updatedAt DESC, name")
    suspend fun entities(scope: String): List<MemoryGraphEntityRecord>

    @Query("SELECT * FROM memory_graph_entities WHERE id IN (:ids)")
    suspend fun entitiesByIds(ids: List<String>): List<MemoryGraphEntityRecord>

    @Query("SELECT * FROM memory_graph_observations WHERE entityId IN (:entityIds) ORDER BY updatedAt DESC")
    suspend fun observationsFor(entityIds: List<String>): List<MemoryGraphObservationRecord>

    @Query("SELECT * FROM memory_graph_relations WHERE fromEntityId IN (:entityIds) OR toEntityId IN (:entityIds) ORDER BY updatedAt DESC")
    suspend fun relationsFor(entityIds: List<String>): List<MemoryGraphRelationRecord>

    @Query(
        """
        SELECT DISTINCT e.* FROM memory_graph_entities e
        LEFT JOIN memory_graph_observations o ON o.entityId = e.id
        WHERE e.scope = :scope AND (
            e.normalizedName LIKE :pattern OR
            LOWER(e.entityType) LIKE :pattern OR
            o.normalizedObservation LIKE :pattern
        )
        ORDER BY e.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchLike(scope: String, pattern: String, limit: Int): List<MemoryGraphEntityRecord>

    @Query("DELETE FROM memory_graph_observations WHERE sourceKind = :sourceKind")
    suspend fun deleteObservationsBySource(sourceKind: String)

    @Query("DELETE FROM memory_graph_relations WHERE sourceKind = :sourceKind")
    suspend fun deleteRelationsBySource(sourceKind: String)

    @Query(
        """
        DELETE FROM memory_graph_entities
        WHERE standalone = 0
          AND id NOT IN (SELECT entityId FROM memory_graph_observations)
          AND id NOT IN (SELECT fromEntityId FROM memory_graph_relations)
          AND id NOT IN (SELECT toEntityId FROM memory_graph_relations)
        """
    )
    suspend fun pruneUnreferencedEntities()

    @Query("DELETE FROM memory_graph_relations")
    suspend fun clearRelations()

    @Query("DELETE FROM memory_graph_observations")
    suspend fun clearObservations()

    @Query("DELETE FROM memory_graph_entities")
    suspend fun clearEntities()
}
