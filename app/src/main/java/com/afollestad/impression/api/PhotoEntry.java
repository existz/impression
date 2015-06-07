package com.afollestad.impression.api;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.provider.MediaStore;
import android.widget.Toast;

import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.utils.Utils;

import java.io.File;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PhotoEntry implements MediaEntry<PhotoEntry> {

    private long _id;
    private String _data;
    private long _size;
    private String title;
    private String _displayName;
    private String mimeType;
    private long dateAdded;
    private long dateTaken;
    private long dateModified;
    private String bucketDisplayName;
    private long bucketId;
    private int width;
    private int height;
    private int mRealIndex;
    public String originalUri;

    public PhotoEntry() {
    }

    @Override
    public PhotoEntry load(Cursor from) {
        PhotoEntry a = new PhotoEntry();
        a._id = from.getLong(from.getColumnIndex(MediaStore.Images.Media._ID));
        a.title = from.getString(from.getColumnIndex(MediaStore.Images.Media.TITLE));
        a._data = from.getString(from.getColumnIndex(MediaStore.Images.Media.DATA));
        a._size = from.getLong(from.getColumnIndex(MediaStore.Images.Media.SIZE));
        a._displayName = from.getString(from.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
        a.mimeType = from.getString(from.getColumnIndex(MediaStore.Images.Media.MIME_TYPE));
        a.dateAdded = from.getLong(from.getColumnIndex(MediaStore.Images.Media.DATE_ADDED));
        a.dateTaken = from.getLong(from.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN));
        a.dateModified = from.getLong(from.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED));
        a.bucketDisplayName = from.getString(from.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
        a.bucketId = from.getLong(from.getColumnIndex(MediaStore.Images.Media.BUCKET_ID));
        a.width = from.getInt(from.getColumnIndex(MediaStore.Images.Media.WIDTH));
        a.height = from.getInt(from.getColumnIndex(MediaStore.Images.Media.HEIGHT));
        return a;
    }

    @Override
    public String[] projection() {
        return new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.TITLE,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
        };
    }

    public static String sort(MediaAdapter.SortMode from) {
        switch (from) {
            default:
                return MediaStore.Images.Media.DISPLAY_NAME + " DESC";
            case NAME_ASC:
                return MediaStore.Images.Media.DISPLAY_NAME + " ASC";
            case MODIFIED_DATE_DESC:
                return MediaStore.Images.Media.DATE_MODIFIED + " DESC";
            case MODIFIED_DATE_ASC:
                return MediaStore.Images.Media.DATE_MODIFIED + " ASC";
        }
    }

    @Override
    public int realIndex() {
        return mRealIndex;
    }

    @Override
    public void setRealIndex(int index) {
        mRealIndex = index;
    }

    @Override
    public long id() {
        return _id;
    }

    @Override
    public String data() {
        return _data;
    }

    @Override
    public long size() {
        return _size;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String displayName() {
        return _displayName;
    }

    @Override
    public String mimeType() {
        return mimeType;
    }

    @Override
    public long dateAdded() {
        return dateAdded;
    }

    @Override
    public long dateModified() {
        return dateModified;
    }

    @Override
    public long dateTaken() {
        return dateTaken;
    }

    @Override
    public String bucketDisplayName() {
        return bucketDisplayName;
    }

    @Override
    public long bucketId() {
        return bucketId;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public boolean isVideo() {
        return false;
    }

    @Override
    public boolean isFolder() {
        return false;
    }

    @Override
    public boolean isAlbum() {
        return false;
    }

    @Override
    public void delete(final Activity context) {
        try {
            final File currentFile = new File(_data);
            context.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Images.Media.DATA + " = ?",
                    new String[]{currentFile.getAbsolutePath()});
            currentFile.delete();
        } catch (final Exception e) {
            e.printStackTrace();
            if (context == null) return;
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public PhotoEntry load(File from) {
        PhotoEntry photoEntry = new PhotoEntry();
        photoEntry._id = -1;
        photoEntry.title = from.getName();
        photoEntry._data = from.getAbsolutePath();
        photoEntry._size = from.length();
        photoEntry._displayName = from.getName();
        photoEntry.mimeType = Utils.getMimeType(Utils.getExtension(from.getName()));
        photoEntry.dateAdded = from.lastModified();
        photoEntry.dateTaken = from.lastModified();
        photoEntry.dateModified = from.lastModified();
        photoEntry.bucketDisplayName = from.getParentFile().getName();
        photoEntry.bucketId = -1;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(from.getAbsolutePath(), options);
        photoEntry.width = options.outWidth;
        photoEntry.height = options.outHeight;
        return photoEntry;
    }
}
