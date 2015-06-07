package com.afollestad.impression.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AsyncCursor {

    private final Context mContext;
    private final Handler mHandler;
    private Uri[] mUris;
    private String[][] mProjections;
    private String[] mSelections;
    private String[][] mSelectionArgs;
    private String[] mSorts;
    private int mLimit;

    public AsyncCursor(Context context) {
        mContext = context;
        mHandler = new Handler();
    }

    public AsyncCursor uris(Uri[] uris) {
        mUris = uris;
        return this;
    }

    public AsyncCursor projections(String[][] projections) {
        mProjections = projections;
        return this;
    }

    public AsyncCursor selections(String[] selections) {
        mSelections = selections;
        return this;
    }

    public AsyncCursor selectionArgs(String[][] args) {
        mSelectionArgs = args;
        return this;
    }

    public AsyncCursor sorts(String[] sorts) {
        mSorts = sorts;
        return this;
    }

    public AsyncCursor limit(int limit) {
        mLimit = limit;
        return this;
    }

    public void query(final Callback callback) {
        if (callback == null)
            throw new IllegalArgumentException("Callback cannot be null.");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Cursor[] results = new Cursor[mUris.length];
                for (int i = 0; i < mUris.length; i++) {
                    String sortMode = mSorts != null ? mSorts[i] : null;
                    if (mLimit > 0) {
                        if (sortMode != null) {
                            sortMode += " LIMIT " + mLimit;
                        } else {
                            sortMode = "_id ASC LIMIT " + mLimit;
                        }
                    }
                    results[i] = mContext.getContentResolver().query(
                            mUris[i],
                            mProjections != null ? mProjections[i] : null,
                            mSelections != null ? mSelections[i] : null,
                            mSelectionArgs != null ? mSelectionArgs[i] : null,
                            sortMode);
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onLoad(results, mUris);
                    }
                });
            }
        }).start();
    }

    public interface Callback {
        void onLoad(Cursor[] data, Uri[] from);
    }
}
