package com.afollestad.impression.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.afollestad.impression.providers.base.ProviderBase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Shirwa Mohamed (shirwaM)
 */
public class ExcludedFolderProvider extends ProviderBase {

    private final static String COLUMNS = "_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT";

    public ExcludedFolderProvider() {
        super("excluded", COLUMNS);
    }

    private final static Uri CONTENT_URI = Uri.parse("content://com.afollestad.impression.excluded");

    public static void clear(Context context) {
        context.getContentResolver().delete(CONTENT_URI, null, null);
    }

    public static void remove(Context context, String path) {
        context.getContentResolver().delete(CONTENT_URI, "path = ?", new String[]{path});
    }

    public static boolean contains(Context context, String path) {
        String selection;
        String[] args;
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("include_subfolders", true)) {
            StringBuilder selectionBuilder = new StringBuilder();
            List<String> argsAry = new ArrayList<>();
            File pathFi = new File(path);
            selectionBuilder.append("path = ?");
            argsAry.add(pathFi.getAbsolutePath());
            while (true) {
                pathFi = pathFi.getParentFile();
                if (pathFi == null) break;
                else if (pathFi.getAbsolutePath().equalsIgnoreCase(Environment.getExternalStorageDirectory().getAbsolutePath()))
                    break;
                selectionBuilder.append(" OR path = ?");
                argsAry.add(pathFi.getAbsolutePath());
            }
            selection = selectionBuilder.toString();
            args = argsAry.toArray(new String[argsAry.size()]);
        } else {
            selection = "path = ?";
            args = new String[]{path};
        }
        Cursor cursor = context.getContentResolver().query(CONTENT_URI, null, selection, args, "_id ASC LIMIT 1");
        boolean contains = false;
        if (cursor != null) {
            contains = cursor.moveToFirst();
            cursor.close();
        }
        return contains;
    }

    public static void add(Context context, String folder) {
        ContentValues values = new ContentValues();
        values.put("path", folder);
        context.getContentResolver().insert(CONTENT_URI, values);
    }

    public static String[] getAll(Context context) {
        List<String> result = new ArrayList<>();
        Cursor cursor = context.getContentResolver().query(CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(1));
            }
            cursor.close();
        }
        return result.toArray(new String[result.size()]);
    }
}