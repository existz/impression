package com.afollestad.impression.providers;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.fragments.base.LoaderFragment;
import com.afollestad.impression.providers.base.ProviderBase;

import java.io.File;

/**
 * @author Shirwa Mohamed (shirwaM)
 */
public class SortMemoryProvider extends ProviderBase {

    private final static String COLUMNS = "_id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, mode INTEGER";

    public SortMemoryProvider() {
        super("sort_memory", COLUMNS);
    }

    private final static Uri CONTENT_URI = Uri.parse("content://com.afollestad.impression.sortmemory");

    public static void cleanup(Context context) {
        final ContentResolver r = context.getContentResolver();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Cursor cursor = r.query(CONTENT_URI, null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        final File fi = new File(cursor.getString(1));
                        if (!fi.exists())
                            r.delete(CONTENT_URI, "path = ?", new String[]{cursor.getString(1)});
                    }
                    cursor.close();
                }
            }
        }).start();
    }

    public static void remember(Context context, String path, MediaAdapter.SortMode mode) {
        if (context == null) return;
        if (path == null) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt("sort_mode", mode.value()).commit();
        } else {
            final ContentResolver r = context.getContentResolver();
            final Cursor cursor = r.query(CONTENT_URI,
                    null, "path = ?", new String[]{path}, null);
            boolean found = false;
            final ContentValues values = new ContentValues(2);
            values.put("path", path);
            values.put("mode", mode.value());
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    found = true;
                    r.update(CONTENT_URI, values, "path = ?", new String[]{path});
                }
                cursor.close();
            }
            if (!found)
                r.insert(CONTENT_URI, values);
        }
    }

    public static MediaAdapter.SortMode remember(Context context, String path) {
        if (context == null) {
            return MediaAdapter.SortMode.valueOf(MediaAdapter.SortMode.DEFAULT);
        } else if (path == null) {
            final int mode = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt("sort_mode", MediaAdapter.SortMode.DEFAULT);
            return MediaAdapter.SortMode.valueOf(mode);
        }
        Cursor cursor = context.getContentResolver().query(CONTENT_URI,
                null, "path = ?", new String[]{path}, null);
        int mode = -1;
        if (cursor != null) {
            if (cursor.moveToFirst())
                mode = cursor.getInt(2);
            cursor.close();
        }
        if (mode == -1) {
            mode = PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt("sort_mode", MediaAdapter.SortMode.DEFAULT);
        }
        return MediaAdapter.SortMode.valueOf(mode);
    }

    public static MediaAdapter.SortMode remember(LoaderFragment context, String path) {
        if (context == null) {
            return MediaAdapter.SortMode.valueOf(MediaAdapter.SortMode.DEFAULT);
        } else if (path == null) {
            final int mode = PreferenceManager.getDefaultSharedPreferences(context.getActivity())
                    .getInt("sort_mode", MediaAdapter.SortMode.DEFAULT);
            return MediaAdapter.SortMode.valueOf(mode);
        }
        Cursor cursor = context.getActivity().getContentResolver().query(CONTENT_URI,
                null, "path = ?", new String[]{path}, null);
        int mode = -1;
        if (cursor != null) {
            if (cursor.moveToFirst())
                mode = cursor.getInt(2);
            cursor.close();
        }
        if (mode == -1) {
            mode = PreferenceManager.getDefaultSharedPreferences(context.getActivity())
                    .getInt("sort_mode", MediaAdapter.SortMode.DEFAULT);
            context.sortRememberDir = false;
        } else {
            context.sortRememberDir = true;
        }
        return MediaAdapter.SortMode.valueOf(mode);
    }

    public static void forget(Context context, String path) {
        if (context == null) return;
        context.getContentResolver().delete(CONTENT_URI, "path = ?", new String[]{path});
    }
}