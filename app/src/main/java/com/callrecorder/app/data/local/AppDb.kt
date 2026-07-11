package com.callrecorder.app.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import kotlinx.coroutines.flow.Flow

/**
 * 디바이스에서 감지된 녹음 파일의 업로드 상태를 추적.
 * 같은 파일이 두 번 업로드되지 않도록 file_path + file_size 로 멱등성 보장.
 *
 * 주의: id는 로컬 PK(Long auto-increment), 서버 ID(storeId, serverCallId)는 String(UUID).
 */
@Entity(
    tableName = "recordings",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,    // 로컬 PK는 Long 유지
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val durationSeconds: Int,
    val callStartedAtMillis: Long,
    val counterpartNumber: String?,
    val storeId: String,                                  // Long → String (서버 UUID)
    val status: String,                                   // PENDING / UPLOADING / UPLOADED / PROCESSING / DONE / FAILED
    val category: String = CallCategory.UNCLASSIFIED,     // BUSINESS / PERSONAL / UNCLASSIFIED (개인정보 보호용 분류)
    val serverCallId: String? = null,                     // Long? → String? (서버 UUID)
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "manual_calendar_events")
data class ManualCalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String, // YYYY-MM-DD
    val time: String,
    val description: String = "",
    val chip: String = "",
    val imageUris: String = "",
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object RecordingStatus {
    const val PENDING = "PENDING"
    const val AWAITING_APPROVAL = "AWAITING_APPROVAL"
    const val UPLOADING = "UPLOADING"
    const val UPLOADED = "UPLOADED"
    const val PROCESSING = "PROCESSING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"   // 사용자가 업로드 큐에서 제거 (재스캔돼도 재업로드 안 함)
}

object CallCategory {
    const val BUSINESS = "BUSINESS"          // 업무 (저장 안 된 번호 = 손님일 가능성)
    const val PERSONAL = "PERSONAL"          // 개인 (연락처에 저장된 이름)
    const val UNCLASSIFIED = "UNCLASSIFIED"  // 미분류 (사용자 수동 분류 대기)
}

/**
 * 발신자 이름/파일명 텍스트만으로 카테고리를 자동 판단.
 * READ_CONTACTS 권한 없이 작동.
 *
 * 규칙:
 * - 숫자/하이픈/공백/+/괄호 만: BUSINESS (저장 안 된 번호)
 * - 한글/영문 포함: PERSONAL (연락처 저장된 이름으로 추정)
 * - null/empty: UNCLASSIFIED
 */
object CallClassifier {
    fun classify(counterpartNumber: String?, fileName: String? = null): String {
        // 1순위: counterpartNumber, 2순위: fileName에서 추출
        val name = counterpartNumber?.takeIf { it.isNotBlank() }
            ?: extractNameFromFileName(fileName)
            ?: return CallCategory.UNCLASSIFIED

        // "통화 녹음 " 같은 prefix 제거
        val cleaned = name
            .removePrefix("통화 녹음 ")
            .removePrefix("통화 녹음")
            .trim()

        if (cleaned.isBlank()) return CallCategory.UNCLASSIFIED

        // 숫자/하이픈/공백/+/괄호 만 있으면 = 저장 안 된 번호 = 업무
        val digitsOnly = cleaned.matches(Regex("^[0-9\\-+\\s()]+$"))
        return if (digitsOnly) CallCategory.BUSINESS else CallCategory.PERSONAL
    }

    /**
     * "통화 녹음 💕내애기💕_260504_123708.m4a" → "통화 녹음 💕내애기💕"
     * 파일명에서 날짜/시간 prefix(_YYMMDD_HHMMSS)와 확장자 제거.
     */
    private fun extractNameFromFileName(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        return fileName
            .substringBeforeLast(".m4a")
            .substringBeforeLast("_2")  // _260504_... 형태 제거 (2020년대 가정)
            .takeIf { it.isNotBlank() }
    }
}

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rec: RecordingEntity): Long

    @Update
    suspend fun update(rec: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun findByPath(path: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings ORDER BY callStartedAtMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    // ===== 카테고리별 조회 (UI 탭용) =====
    @Query("SELECT * FROM recordings WHERE category = :category ORDER BY callStartedAtMillis DESC")
    fun observeByCategory(category: String): Flow<List<RecordingEntity>>

    @Query("SELECT COUNT(*) FROM recordings WHERE category = :category")
    fun observeCountByCategory(category: String): Flow<Int>

    // ===== 업로드 큐 =====
    @Query("""
        SELECT * FROM recordings
        WHERE status = 'PENDING'
           OR (status = 'FAILED' AND serverCallId IS NULL)
        ORDER BY createdAt ASC
    """)
    suspend fun pending(): List<RecordingEntity>

    /**
     * 카테고리 필터링된 업로드 큐.
     * 기본적으로 BUSINESS + UNCLASSIFIED만 업로드 (PERSONAL은 사용자 동의 시에만).
     */
    @Query("""
        SELECT * FROM recordings 
        WHERE (status = 'PENDING' OR (status = 'FAILED' AND serverCallId IS NULL))
          AND category IN (:allowedCategories)
        ORDER BY createdAt ASC
    """)
    suspend fun pendingByCategory(allowedCategories: List<String>): List<RecordingEntity>

    // ===== 상태 업데이트 =====
    @Query("UPDATE recordings SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    // callId: Long → String
    @Query("UPDATE recordings SET serverCallId = :callId, status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setServerCallId(id: Long, callId: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE recordings SET storeId = :storeId, updatedAt = :now WHERE id = :id")
    suspend fun updateStoreId(id: Long, storeId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE recordings SET status = :status, errorMessage = :err, updatedAt = :now WHERE id = :id")
    suspend fun setError(id: Long, status: String, err: String?, now: Long = System.currentTimeMillis())

    // ===== 카테고리 변경 (사용자 수동) =====
    @Query("UPDATE recordings SET category = :category, updatedAt = :now WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM recordings WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /** 진행 중/실패 목록 개수 실시간 관찰 — 홈 진행 칩용 */
    @Query("SELECT COUNT(*) FROM recordings WHERE status IN ('PENDING','UPLOADING','UPLOADED','PROCESSING','FAILED')")
    fun observeActiveUploadCount(): Flow<Int>

    /** 진행 중/실패 목록 실시간 관찰 — 분석 대기 UI용 */
    @Query("SELECT * FROM recordings WHERE status IN ('PENDING','UPLOADING','UPLOADED','PROCESSING','FAILED') ORDER BY createdAt ASC")
    fun observeActiveUploads(): Flow<List<RecordingEntity>>

    /** 서버 처리 중(업로드 완료~분석)인 로컬 녹음 — 완료 동기화용 */
    @Query("SELECT * FROM recordings WHERE status IN ('UPLOADED','PROCESSING') AND serverCallId IS NOT NULL")
    suspend fun getServerProcessing(): List<RecordingEntity>

    /** 서버 분석 취소 API를 같이 호출해야 하는 로컬 녹음 */
    @Query("SELECT * FROM recordings WHERE status IN ('UPLOADED','PROCESSING') AND serverCallId IS NOT NULL")
    suspend fun getCancelableServerUploads(): List<RecordingEntity>

    /** 서버 요약 완료 시 로컬 상태를 DONE으로 정리 (진행 칩에서 제거) */
    @Query("UPDATE recordings SET status='DONE', updatedAt=:now WHERE serverCallId = :callId")
    suspend fun markDoneByServerCallId(callId: String, now: Long = System.currentTimeMillis())

    /** 안전장치: 일정 시간 넘게 분석중(UPLOADED/PROCESSING)인 건을 강제로 DONE 처리해 진행 칩에서 내림 */
    @Query("UPDATE recordings SET status='DONE', updatedAt=:now WHERE status IN ('UPLOADED','PROCESSING') AND updatedAt < :threshold")
    suspend fun markStaleProcessingDone(threshold: Long, now: Long = System.currentTimeMillis())

    /** 진행 중(대기/업로드/분석) 전체를 일괄 취소 */
    @Query("UPDATE recordings SET status='CANCELED', updatedAt=:now WHERE status IN ('PENDING','UPLOADING','UPLOADED','PROCESSING')")
    suspend fun cancelAllActive(now: Long = System.currentTimeMillis())

    @Query("UPDATE recordings SET status = 'PENDING', updatedAt = :now WHERE status = 'AWAITING_APPROVAL'")
    suspend fun approveAll(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM recordings WHERE status = 'AWAITING_APPROVAL' ORDER BY createdAt DESC")
    suspend fun getAwaitingApproval(): List<RecordingEntity>

    @Query("""
        SELECT COUNT(*) FROM recordings
        WHERE fileName = :fileName
          AND fileSize = :fileSize
          AND status NOT IN ('CANCELED', 'FAILED')
    """)
    suspend fun countActiveByFileNameAndSize(fileName: String, fileSize: Long): Int

    @Query("UPDATE recordings SET status = 'PENDING', updatedAt = :now WHERE id = :id")
    suspend fun approveOne(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteOne(id: Long)

    @Query("DELETE FROM recordings WHERE serverCallId = :callId")
    suspend fun deleteByServerCallId(callId: String)
}

@Dao
interface ManualCalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ManualCalendarEventEntity)

    @Query("""
        SELECT * FROM manual_calendar_events
        WHERE date BETWEEN :fromDate AND :toDate
        ORDER BY date ASC, time ASC, title ASC
    """)
    suspend fun getInRange(fromDate: String, toDate: String): List<ManualCalendarEventEntity>

    @Query("DELETE FROM manual_calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `manual_calendar_events` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `time` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `chip` TEXT NOT NULL,
                `imageUris` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `manual_calendar_events` ADD COLUMN `reminderEnabled` INTEGER NOT NULL DEFAULT 1"
        )
    }
}

@Database(
    entities = [RecordingEntity::class, ManualCalendarEventEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun manualCalendarEventDao(): ManualCalendarEventDao
}
