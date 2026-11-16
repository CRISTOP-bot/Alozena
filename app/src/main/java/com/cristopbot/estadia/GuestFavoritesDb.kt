package com.cristopbot.estadia

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class GuestFavoritesDb(context: Context) : SQLiteOpenHelper(context, "estadia-local.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE guest_favorites (stay_id INTEGER PRIMARY KEY NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS guest_favorites (stay_id INTEGER PRIMARY KEY NOT NULL)")
    }

    fun readFavorites(): Set<Int> = readableDatabase.rawQuery("SELECT stay_id FROM guest_favorites", null).use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) }
    }

    fun setFavorite(stayId: Int, saved: Boolean) {
        if (saved) writableDatabase.execSQL("INSERT OR IGNORE INTO guest_favorites(stay_id) VALUES (?)", arrayOf(stayId))
        else writableDatabase.delete("guest_favorites", "stay_id = ?", arrayOf(stayId.toString()))
    }
}

