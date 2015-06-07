package com.afollestad.impression.api.base;

import android.app.Activity;

import java.io.File;
import java.io.Serializable;

public interface MediaEntry<T> extends CursorItem<T>, Serializable {

    int realIndex();

    void setRealIndex(int index);

    long id();

    String data();

    long size();

    String title();

    String displayName();

    String mimeType();

    long dateAdded();

    long dateModified();

    long dateTaken();

    String bucketDisplayName();

    long bucketId();

    int width();

    int height();

    boolean isVideo();

    boolean isFolder();

    boolean isAlbum();

    void delete(Activity context);

    T load(File from);
}
