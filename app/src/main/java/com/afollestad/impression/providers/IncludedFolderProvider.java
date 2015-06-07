package com.afollestad.impression.providers;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.afollestad.impression.adapters.NavDrawerAdapter;
import com.afollestad.impression.providers.base.ProviderBase;

import java.io.File;

/**
 * @author Shirwa Mohamed (shirwaM)
 */
public class IncludedFolderProvider extends ProviderBase {

    private final static String COLUMNS = "_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT";

    public IncludedFolderProvider() {
        super("included", COLUMNS);
    }

    public final static Uri CONTENT_URI = Uri.parse("content://com.afollestad.impression.included");

    public static void clear(Context context) {
        context.getContentResolver().delete(CONTENT_URI, null, null);
    }

    public static void remove(Context context, String path) {
        context.getContentResolver().delete(CONTENT_URI, "path = ?", new String[]{path});
    }

    public static void add(Context context, File folder) {
        ContentValues values = new ContentValues();
        values.put("path", folder.getAbsolutePath());
        context.getContentResolver().insert(CONTENT_URI, values);
    }
}