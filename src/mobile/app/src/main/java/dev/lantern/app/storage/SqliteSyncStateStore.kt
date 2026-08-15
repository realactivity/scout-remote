package dev.lantern.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.lantern.core.platform.SecureStorage
import dev.lantern.core.sync.CachedFile
import dev.lantern.core.sync.SyncStateStore
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * SQLite metadata index with payload bytes kept in Android Keystore-backed
 * storage. The database contains paths, ETags, item IDs, and cursors only.
 */
class SqliteSyncStateStore(
    context: Context,
    private val secureStorage: SecureStorage,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), SyncStateStore {
    private val mutex = Mutex()

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE sync_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        database.execSQL(
            """
            CREATE TABLE sync_items (
                path TEXT PRIMARY KEY,
                item_id TEXT NOT NULL,
                etag TEXT NOT NULL,
                last_modified_ms INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX sync_items_item_id ON sync_items(item_id)")
        database.execSQL("CREATE TABLE watched_commands (command_id TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == newVersion) {
            "No sync database migration from $oldVersion to $newVersion"
        }
    }

    override suspend fun deltaCursor(): String? = meta(DELTA_CURSOR)

    override suspend fun setDeltaCursor(cursor: String?) {
        setMeta(DELTA_CURSOR, cursor)
    }

    override suspend fun cachedFile(path: String): CachedFile? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readableDatabase.query(
                "sync_items",
                ITEM_COLUMNS,
                "path = ?",
                arrayOf(path),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@withContext null
                }
                cursor.toCachedFile(secureStorage.get(payloadKey(path)))
            }
        }
    }

    override suspend fun cachedFiles(prefix: String): List<CachedFile> {
        val metadata = mutex.withLock {
            withContext(Dispatchers.IO) {
                val selection = if (prefix.isEmpty()) null else "path = ? OR path LIKE ?"
                val selectionArgs = if (prefix.isEmpty()) {
                    null
                } else {
                    arrayOf(prefix, "${prefix.trimEnd('/')}/%")
                }
                val rows = mutableListOf<CachedFile>()
                readableDatabase.query(
                    "sync_items",
                    ITEM_COLUMNS,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    "path ASC",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        rows += cursor.toCachedFile(content = null)
                    }
                }
                rows
            }
        }
        return metadata.map { file ->
            file.copy(content = secureStorage.get(payloadKey(file.path)))
        }
    }

    override suspend fun putCachedFile(file: CachedFile) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val existingEtag = etagFor(file.path)
                val content = file.content
                if (content != null) {
                    secureStorage.set(payloadKey(file.path), content)
                }
                val values = ContentValues().apply {
                    put("path", file.path)
                    put("item_id", file.itemId)
                    put("etag", file.etag)
                    put("last_modified_ms", file.lastModifiedUtc.toEpochMilliseconds())
                }
                writableDatabase.insertWithOnConflict(
                    "sync_items",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                if (content == null && existingEtag != null && existingEtag != file.etag) {
                    secureStorage.remove(payloadKey(file.path))
                }
            }
        }
    }

    override suspend fun removeCachedFile(path: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                writableDatabase.delete("sync_items", "path = ?", arrayOf(path))
                secureStorage.remove(payloadKey(path))
            }
        }
    }

    override suspend fun removeCachedFileByItemId(itemId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val paths = mutableListOf<String>()
                readableDatabase.query(
                    "sync_items",
                    arrayOf("path"),
                    "item_id = ?",
                    arrayOf(itemId),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        paths += cursor.getString(0)
                    }
                }
                writableDatabase.delete("sync_items", "item_id = ?", arrayOf(itemId))
                paths.forEach { secureStorage.remove(payloadKey(it)) }
            }
        }
    }

    override suspend fun clearCachedFiles() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val paths = mutableListOf<String>()
                readableDatabase.query(
                    "sync_items",
                    arrayOf("path"),
                    null,
                    null,
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        paths += cursor.getString(0)
                    }
                }
                writableDatabase.delete("sync_items", null, null)
                paths.forEach { secureStorage.remove(payloadKey(it)) }
            }
        }
    }

    override suspend fun lastActivitySince(): Instant? =
        meta(LAST_ACTIVITY)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)

    override suspend fun setLastActivitySince(value: Instant) {
        setMeta(LAST_ACTIVITY, value.toEpochMilliseconds().toString())
    }

    override suspend fun watchedCommandIds(): Set<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            buildSet {
                readableDatabase.query(
                    "watched_commands",
                    arrayOf("command_id"),
                    null,
                    null,
                    null,
                    null,
                    "command_id ASC",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
        }
    }

    override suspend fun setCommandWatched(commandId: String, watched: Boolean) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (watched) {
                    writableDatabase.insertWithOnConflict(
                        "watched_commands",
                        null,
                        ContentValues().apply { put("command_id", commandId) },
                        SQLiteDatabase.CONFLICT_IGNORE,
                    )
                } else {
                    writableDatabase.delete(
                        "watched_commands",
                        "command_id = ?",
                        arrayOf(commandId),
                    )
                }
            }
        }
    }

    private suspend fun meta(key: String): String? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readableDatabase.query(
                "sync_meta",
                arrayOf("value"),
                "key = ?",
                arrayOf(key),
                null,
                null,
                null,
                "1",
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
    }

    private suspend fun setMeta(key: String, value: String?) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (value == null) {
                    writableDatabase.delete("sync_meta", "key = ?", arrayOf(key))
                } else {
                    writableDatabase.insertWithOnConflict(
                        "sync_meta",
                        null,
                        ContentValues().apply {
                            put("key", key)
                            put("value", value)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
        }
    }

    private fun etagFor(path: String): String? =
        readableDatabase.query(
            "sync_items",
            arrayOf("etag"),
            "path = ?",
            arrayOf(path),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun android.database.Cursor.toCachedFile(content: ByteArray?): CachedFile =
        CachedFile(
            path = getString(0),
            itemId = getString(1),
            etag = getString(2),
            lastModifiedUtc = Instant.fromEpochMilliseconds(getLong(3)),
            content = content,
        )

    private fun payloadKey(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
        return "sync.payload." + digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DATABASE_NAME = "lantern_sync.db"
        private const val DATABASE_VERSION = 1
        private const val DELTA_CURSOR = "delta_cursor"
        private const val LAST_ACTIVITY = "last_activity_since"
        private val ITEM_COLUMNS = arrayOf("path", "item_id", "etag", "last_modified_ms")
    }
}
