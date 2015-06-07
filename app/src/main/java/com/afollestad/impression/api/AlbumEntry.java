package com.afollestad.impression.api;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.providers.SortMemoryProvider;
import com.afollestad.impression.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AlbumEntry implements MediaEntry<AlbumEntry> {

    public static final String ALBUM_OVERVIEW = Environment.getExternalStorageDirectory().getAbsolutePath();

    private final File mFile;
    private int mSize;
    private long mAlbumId;
    private Map<String, LoaderEntry> loadedHolder;
    public String mFirstPath;
    private int mRealIndex;

    public void processLoaded(Context context) {
        if (loadedHolder == null) return;
        mSize = loadedHolder.size();
        MediaAdapter.SortMode sort = SortMemoryProvider.remember(context, mFile.getAbsolutePath());
        List<LoaderEntry> mEntries = new ArrayList<>(loadedHolder.values());
        Collections.sort(mEntries, new LoaderEntry.Sorter(sort));
        mFirstPath = mEntries.get(0).data();
    }

    public void putLoaded(LoaderEntry entry) {
        if (loadedHolder == null)
            loadedHolder = new HashMap<>();
        loadedHolder.put(entry.data(), entry);
    }

    public void setBucketId(long id) {
        mAlbumId = id;
    }

    /**
     * Used for cursor albums.
     */
    public AlbumEntry(String fromPath, long albumId) {
        mFile = new File(fromPath);
        mAlbumId = albumId;
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
        return -1;
    }

    @Override
    public String data() {
        return mFile.getAbsolutePath();
    }

    @Override
    public String title() {
        return mFile.getName();
    }

    @Override
    public long size() {
        return mSize;
    }

    @Override
    public String displayName() {
        return mFile.getName();
    }

    @Override
    public String mimeType() {
        return null;
    }

    @Override
    public long dateAdded() {
        return mFile.lastModified();
    }

    @Override
    public long dateModified() {
        return mFile.lastModified();
    }

    @Override
    public long dateTaken() {
        return -1;
    }

    @Override
    public String bucketDisplayName() {
        return mFile.getParentFile().getName();
    }

    @Override
    public long bucketId() {
        return mAlbumId;
    }

    @Override
    public int width() {
        return -1;
    }

    @Override
    public int height() {
        return -1;
    }

    @Override
    public boolean isVideo() {
        return false;
    }

    @Override
    public boolean isFolder() {
        return mAlbumId == ALBUM_ID_USEPATH;
    }

    @Override
    public boolean isAlbum() {
        return true;
    }

    @Override
    public void delete(Activity context) {
        if (bucketId() == ALBUM_ID_ROOT) {
            throw new RuntimeException("You can't delete the root directory.");
        } else if (bucketId() != ALBUM_ID_USEPATH) {
            try {
                context.getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Images.Media.BUCKET_ID + " = " + bucketId(), null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Utils.deleteFolder(mFile);
    }

    public MediaEntry[] getContents(Context context, boolean getSubFolders) {
        final boolean is = getSubFolders && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("include_subfolders_included", true);
        final List<MediaEntry> results = Utils.getEntriesFromFolder(context, mFile, false, is, MediaAdapter.FileFilterMode.ALL);
        return results.toArray(new MediaEntry[results.size()]);
    }

    @Override
    public AlbumEntry load(File from) {
        return null;
    }

    @Override
    public AlbumEntry load(Cursor from) {
        return null;
    }

    @Override
    public String[] projection() {
        return null;
    }

    public static final long ALBUM_ID_USEPATH = -1;
    private static final long ALBUM_ID_ROOT = -2;
}