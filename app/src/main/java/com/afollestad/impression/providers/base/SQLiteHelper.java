package com.afollestad.impression.providers.base;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class SQLiteHelper extends SQLiteOpenHelper {

    private static final String DATABASE = "CoupCash";
    private static final int DATABASE_VERSION = 3;
    private final String TABLE;

    public SQLiteHelper(Context context, String table, String columns) {
        super(context, DATABASE, null, DATABASE_VERSION);
        TABLE = table;
        try {
            String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + table + " (" + columns + ");";
            Log.d("Provider", TABLE_CREATE);
            getWritableDatabase().execSQL(TABLE_CREATE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(SQLiteHelper.class.getName(), "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }
}