package com.afollestad.impression.accounts;

import android.content.Context;

import com.afollestad.impression.accounts.base.Account;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.AlbumEntry;

/**
 * @author Aidan Follestad (afollestad)
 */
public class GoogleDriveAccount extends Account {

    private final int mId;
    private final String mName;

    public GoogleDriveAccount(Context context, String name, int id) {
        super(context);
        mId = id;
        mName = name;
    }

    @Override
    public int id() {
        return mId;
    }

    @Override
    public int type() {
        return TYPE_GOOGLE_DRIVE;
    }

    @Override
    public String name() {
        return mName;
    }

    @Override
    public boolean hasIncludedFolders() {
        return false;
    }

    @Override
    public void getAlbums(MediaAdapter.SortMode sort, MediaAdapter.FileFilterMode filter, AlbumCallback callback) {
        callback.onAlbums(null);
    }

    @Override
    public void getIncludedFolders(AlbumEntry[] preEntries, AlbumCallback callback) {
    }

    @Override
    public void getEntries(String albumPath, int overviewMode, boolean explorerMode, MediaAdapter.FileFilterMode filter, MediaAdapter.SortMode sort, EntriesCallback callback) {
    }
}
