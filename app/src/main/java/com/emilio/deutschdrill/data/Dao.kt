package com.emilio.deutschdrill.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DrillDao {
    // Cards
    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun card(id: String): CardEntity?

    @Query("SELECT * FROM cards")
    suspend fun allCards(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE state != 0 AND due <= :now ORDER BY due LIMIT :limit")
    suspend fun dueCards(now: Long, limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE state = 0 ORDER BY introScore DESC, createdAt, id LIMIT :limit")
    suspend fun newCards(limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE state != 0 ORDER BY due LIMIT :limit")
    suspend fun soonestCards(limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE (' ' || tags || ' ') LIKE '% ' || :tag || ' %' ORDER BY due")
    suspend fun cardsWithTag(tag: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE state != 0 AND due <= :now")
    fun dueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE state = 0")
    fun newCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    fun cardCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE introducedAt >= :since")
    suspend fun introducedSince(since: Long): Int

    @Query("SELECT tags FROM cards")
    suspend fun allTagStrings(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    // Reviews
    @Insert
    suspend fun insertReview(review: ReviewEntity): Long

    @Update
    suspend fun updateReview(review: ReviewEntity)

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun review(id: Long): ReviewEntity?

    @Query("SELECT * FROM reviews ORDER BY timestamp")
    suspend fun allReviews(): List<ReviewEntity>

    @Query("SELECT * FROM reviews ORDER BY timestamp")
    fun reviewsFlow(): Flow<List<ReviewEntity>>

    // Sessions
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    fun sessionsFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun allSessions(): List<SessionEntity>

    // Own sentences
    @Insert
    suspend fun insertSentence(sentence: OwnSentenceEntity)

    @Query("SELECT * FROM own_sentences ORDER BY timestamp")
    suspend fun allSentences(): List<OwnSentenceEntity>
}
