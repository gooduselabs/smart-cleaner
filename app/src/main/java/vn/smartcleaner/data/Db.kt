package vn.smartcleaner.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hash")
data class HashEntity(@PrimaryKey val id: Long, val modified: Long, val size: Long, val dHash: Long, val sharpness: Double, val sha: String?)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val key: Long = 0,
    val uri: String, val name: String, val size: Long, val mime: String,
    val trashedAt: Long, val kind: String = KIND_MEDIA,
    /** Với tệp SAF: thư mục cha để khôi phục và bản sao trong bộ nhớ riêng. */
    val parentUri: String? = null, val privatePath: String? = null,
) { companion object { const val KIND_MEDIA = "media"; const val KIND_SAF = "saf" } }

@Dao interface HashDao {
    @Query("SELECT * FROM hash") suspend fun all(): List<HashEntity>
    @Upsert suspend fun put(list: List<HashEntity>)
}

@Dao interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY trashedAt DESC") fun flow(): Flow<List<TrashEntity>>
    @Query("SELECT * FROM trash") suspend fun all(): List<TrashEntity>
    @Insert suspend fun add(list: List<TrashEntity>)
    @Delete suspend fun remove(list: List<TrashEntity>)
}

@Database(entities = [HashEntity::class, TrashEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun hash(): HashDao
    abstract fun trash(): TrashDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context) = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "cleaner.db").build().also { inst = it }
        }
    }
}
