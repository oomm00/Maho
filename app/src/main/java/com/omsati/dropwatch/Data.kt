package com.omsati.dropwatch

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "📁",
    val color: Long = 0xFF4F46E5L, // Stored as Long (ARGB color int)
    val sortOption: String = "MY_ORDER",
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val domain: String,
    val title: String,
    val imageUrl: String? = null,
    val price: Double? = null,
    val previousPrice: Double? = null,
    val currency: String? = null,
    val targetPrice: Double? = null,
    val folderId: Long? = null,
    val category: String = "Other",
    val addedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long = 0,
    /** fetching | ok | no_price | error */
    val status: String = "fetching",
    val position: Int = 0,
    val note: String? = null,
)

@Entity(tableName = "price_history")
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val price: Double,
    val at: Long,
)

@Dao
interface StoreDao {
    @Query("SELECT * FROM products ORDER BY addedAt DESC") fun products(): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE id = :id") fun product(id: Long): Flow<Product?>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun productById(id: Long): Product?
    @Query("SELECT * FROM products") suspend fun allProducts(): List<Product>
    @Query("SELECT DISTINCT folderId FROM products WHERE id IN (:ids)") suspend fun getFolderIdsForProducts(ids: List<Long>): List<Long?>
    @Query("SELECT * FROM products WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId ORDER BY position ASC, addedAt ASC")
    suspend fun productsInFolder(folderId: Long?): List<Product>
    @Query("SELECT MAX(position) FROM products WHERE (:folderId IS NULL AND folderId IS NULL) OR folderId = :folderId")
    suspend fun maxPosition(folderId: Long?): Int?

    @Insert suspend fun insert(p: Product): Long
    @Update suspend fun update(p: Product)
    @Query("UPDATE products SET position = :position WHERE id = :id") suspend fun updateProductPosition(id: Long, position: Int)
    @Query("UPDATE products SET note = :note WHERE id = :id") suspend fun updateProductNote(id: Long, note: String?)
    @Query("DELETE FROM products WHERE id = :id") suspend fun deleteProduct(id: Long)
    @Query("DELETE FROM products WHERE id IN (:ids)") suspend fun deleteProducts(ids: List<Long>)

    @Query("SELECT * FROM folders ORDER BY name") fun folders(): Flow<List<Folder>>
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun folderById(id: Long): Folder?
    @Insert suspend fun insertFolder(f: Folder): Long
    @Update suspend fun updateFolder(f: Folder)
    @Query("UPDATE folders SET sortOption = :sortOption WHERE id = :id") suspend fun updateFolderSortOption(id: Long, sortOption: String)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun deleteFolder(id: Long)
    @Query("UPDATE products SET folderId = NULL WHERE folderId = :id") suspend fun clearFolder(id: Long)

    @Insert suspend fun insertPoint(p: PricePoint)
    @Query("SELECT * FROM price_history WHERE productId = :id ORDER BY at") fun history(id: Long): Flow<List<PricePoint>>
    @Query("SELECT * FROM price_history WHERE productId = :id ORDER BY at DESC LIMIT 1") suspend fun latestPoint(id: Long): PricePoint?
    @Query("DELETE FROM price_history WHERE at < :threshold") suspend fun pruneOldHistory(threshold: Long)
    @Query("DELETE FROM price_history WHERE productId = :id") suspend fun deleteHistory(id: Long)
    @Query("DELETE FROM price_history WHERE productId IN (:ids)") suspend fun deleteHistoryForProducts(ids: List<Long>)
}

@Database(entities = [Folder::class, Product::class, PricePoint::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): StoreDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN color INTEGER NOT NULL DEFAULT 4283336421")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN sortOption TEXT NOT NULL DEFAULT 'MY_ORDER'")
                db.execSQL("ALTER TABLE products ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN note TEXT")

                // For existing products, set position by their current date-added order, per folder
                val cursor = db.query("SELECT id, folderId FROM products ORDER BY addedAt ASC")
                val folderCounts = mutableMapOf<Long?, Int>()
                val updates = mutableListOf<Pair<Long, Int>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val folderId = if (cursor.isNull(1)) null else cursor.getLong(1)
                    val currentPos = folderCounts.getOrDefault(folderId, 0)
                    updates.add(Pair(id, currentPos))
                    folderCounts[folderId] = currentPos + 1
                }
                cursor.close()

                for ((id, pos) in updates) {
                    db.execSQL("UPDATE products SET position = $pos WHERE id = $id")
                }
            }
        }

        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "dropwatch.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { inst = it }
        }
    }
}
