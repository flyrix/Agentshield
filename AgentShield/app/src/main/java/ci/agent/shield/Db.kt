package ci.agent.shield

import android.content.Context
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.UUID

@Entity(tableName = "events")
data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
                       val ts: Long, val source: String, val text: String, val risk: String)

@Dao
interface EventDao {
    @Insert suspend fun insert(e: EventEntity)
    @Query("SELECT * FROM events ORDER BY ts DESC LIMIT 50") fun recent(): Flow<List<EventEntity>>
}

/** Base SQLCipher : la clé est générée une fois et stockée chiffrée (Android Keystore). */
@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): EventDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: build(c.applicationContext).also { inst = it }
        }
        private fun build(c: Context): AppDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(c, AppDb::class.java, "agent.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase(c))).build()
        }
        private fun passphrase(c: Context): ByteArray {
            val mk = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val p = EncryptedSharedPreferences.create(c, "sec", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            val k = p.getString("k", null) ?: (UUID.randomUUID().toString() + UUID.randomUUID())
                .also { p.edit().putString("k", it).apply() }
            return k.toByteArray()
        }
    }
}
