package com.mediaproxy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject

class MediaCache(context: Context) {

    private val db: SQLiteDatabase

    init {
        val path = context.getDatabasePath("bdix_hub.db").absolutePath
        db = SQLiteDatabase.openOrCreateDatabase(path, null)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS entries (
                path TEXT PRIMARY KEY,
                source TEXT,
                name TEXT,
                is_dir INTEGER,
                parent TEXT,
                cached_at INTEGER
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_parent ON entries(parent)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_name ON entries(name)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meta (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT,
                year TEXT,
                json TEXT,
                cached_at INTEGER
            )
        """)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_meta_query ON meta(query, year)")
    }

    fun insertBatch(parentPath: String, entries: List<WebDAVServer.IndexEntry>) {
        val source = parentPath.trim('/').substringBefore('/')
        db.beginTransaction()
        try {
            for (e in entries) {
                val childPath = parentPath.trimEnd('/') + "/" + e.displayName.trimEnd('/')
                db.execSQL("""
                    INSERT OR REPLACE INTO entries (path, source, name, is_dir, parent, cached_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, arrayOf(
                    childPath,
                    source,
                    e.displayName.trimEnd('/'),
                    if (e.isDir) 1 else 0,
                    parentPath,
                    System.currentTimeMillis()
                ))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    data class SearchResult(val path: String, val name: String, val isDir: Boolean, val source: String)

    fun search(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val cursor = db.rawQuery(
            "SELECT path, name, is_dir, source FROM entries WHERE name LIKE ? LIMIT 100",
            arrayOf("%$query%")
        )
        while (cursor.moveToNext()) {
            results.add(SearchResult(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getInt(2) == 1,
                cursor.getString(3)
            ))
        }
        cursor.close()
        return results
    }

    fun getMeta(title: String, year: String?): String? {
        val cursor = db.rawQuery(
            "SELECT json FROM meta WHERE query = ? AND (year = ? OR year IS NULL) ORDER BY cached_at DESC LIMIT 1",
            arrayOf(title.lowercase(), year)
        )
        val result = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return result
    }

    fun putMeta(title: String, year: String?, json: String) {
        db.execSQL("""
            INSERT OR REPLACE INTO meta (query, year, json, cached_at)
            VALUES (?, ?, ?, ?)
        """, arrayOf(title.lowercase(), year, json, System.currentTimeMillis()))
    }

    fun clearOldEntries(olderThanMs: Long) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        db.execSQL("DELETE FROM entries WHERE cached_at < ?", arrayOf(cutoff.toString()))
    }
}
