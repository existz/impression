package com.afollestad.impression.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;

import com.afollestad.impression.R;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.FolderEntry;
import com.afollestad.impression.api.PhotoEntry;
import com.afollestad.impression.api.VideoEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.providers.ExcludedFolderProvider;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Utils {

    public static Uri getImageContentUri(Context context, File imageFile) {
        if (context == null) return null;
        final String mimeType = Utils.getMimeType(Utils.getExtension(imageFile.getName()));
        Uri providerUri;
        if (mimeType != null && mimeType.startsWith("video/")) {
            providerUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            providerUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(
                providerUri,
                new String[]{"_id"},
                "_data = ? ",
                new String[]{filePath}, null);
        if (cursor != null) {
            Uri uri = null;
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex("_id"));
                uri = Uri.withAppendedPath(providerUri, "" + id);
            }
            cursor.close();
            return uri;
        } else {
            if (imageFile.exists()) {
                ContentValues values = new ContentValues();
                values.put("_data", filePath);
                return context.getContentResolver().insert(providerUri, values);
            } else {
                return null;
            }
        }
    }

    public static int resolveDrawable(Context context, int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getResourceId(0, 0);
        } finally {
            a.recycle();
        }
    }

    public static int resolveColor(Context context, int attr) {
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{attr});
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    public static String readableFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getExtension(String name) {
        if (name == null) return null;
        name = name.toLowerCase();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public static String getMimeType(String extension) {
        String type = null;
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension);
        }
        return type;
    }

    public static void showErrorDialog(Context context, Exception e) {
        try {
            new MaterialDialog.Builder(context)
                    .title(R.string.error)
                    .content(e.getMessage())
                    .positiveText(android.R.string.ok)
                    .build()
                    .show();
        } catch (WindowManager.BadTokenException e2) {
            e.printStackTrace();
            e2.printStackTrace();
        }
    }

    public static double roundToDecimals(double d, int c) {
        int temp = (int) (d * Math.pow(10, c));
        return ((double) temp) / Math.pow(10, c);
    }

    public static void deleteFolder(File folder) {
        File[] contents = folder.listFiles();
        if (contents != null && contents.length > 0) {
            for (File fi : contents) {
                if (fi.isDirectory()) {
                    deleteFolder(fi);
                } else {
                    fi.delete();
                }
            }
        }
        folder.delete();
    }

    public static List<MediaEntry> getEntriesFromFolder(Context context, File dir, boolean includeFolders, boolean includeFoldersContents, MediaAdapter.FileFilterMode filter) {
        final File[] content = dir.listFiles();
        final List<MediaEntry> results = new ArrayList<>();
        if (content != null) {
            for (File fi : content) {
                if (!fi.isHidden()) {
                    if (fi.isDirectory()) {
                        if (includeFolders || includeFoldersContents) {
                            if (ExcludedFolderProvider.contains(context, fi.getAbsolutePath()))
                                continue;
                            if (includeFoldersContents) {
                                final List<MediaEntry> subContents = Utils.getEntriesFromFolder(context, fi, includeFolders, true, filter);
                                results.addAll(subContents);
                            } else {
                                results.add(new FolderEntry(fi));
                            }
                        }
                    } else {
                        String mime = Utils.getMimeType(Utils.getExtension(fi.getName()));
                        if (mime != null) {
                            if (mime.startsWith("image/") && filter != MediaAdapter.FileFilterMode.VIDEOS) {
                                results.add(new PhotoEntry().load(fi));
                            } else if (mime.startsWith("video/") && filter != MediaAdapter.FileFilterMode.PHOTOS) {
                                results.add(new VideoEntry().load(fi));
                            }
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * Returns a string representation of {@param set}. Used only for debugging purposes.
     */
    @NonNull
    public static String setToString(@NonNull Set<String> set) {
        Iterator<String> i = set.iterator();
        if (!i.hasNext()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder().append('[');
        while (true) {
            sb.append(i.next());
            if (!i.hasNext()) {
                return sb.append(']').toString();
            }
            sb.append(", ");
        }
    }

    private Utils() {
        // This utility class should not be instantiated.
    }
}
