package com.afollestad.impression.providers.base;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class ProviderBase extends ContentProvider {

    private final String TABLE;
    private final String COLUMNS;
    private SQLiteHelper database;

    protected ProviderBase(String tableName, String columns) {
        TABLE = tableName;
        COLUMNS = columns;
    }

    @Override
    public boolean onCreate() {
        database = new SQLiteHelper(getContext(), TABLE, COLUMNS);
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return database.getReadableDatabase().query(TABLE, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        database.getWritableDatabase().insert(TABLE, null, values);
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return database.getWritableDatabase().delete(TABLE, selection, selectionArgs);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return database.getWritableDatabase().update(TABLE, values, selection, selectionArgs);
    }
}