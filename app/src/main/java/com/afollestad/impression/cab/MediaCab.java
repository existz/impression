package com.afollestad.impression.cab;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.impression.R;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.AlbumEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.fragments.MediaFragment;
import com.afollestad.impression.providers.ExcludedFolderProvider;
import com.afollestad.impression.ui.MainActivity;
import com.afollestad.impression.ui.viewer.ViewerActivity;
import com.afollestad.impression.utils.TimeUtils;
import com.afollestad.impression.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MediaCab implements ActionMode.Callback, Serializable {

    private final transient MediaFragment mContext;
    private final List<MediaEntry> mMediaEntries;
    private ActionMode mActionMode;

    public final static int COPY_REQUEST_CODE = 8000;
    public final static int MOVE_REQUEST_CODE = 9000;

    public MediaCab(MediaFragment context) {
        mContext = context;
        mMediaEntries = new ArrayList<>();
    }

    public MediaCab(MediaFragment context, ViewerActivity.MediaWrapper instanceState) {
        mContext = context;
        mMediaEntries = instanceState.getMedia();
    }

    public ViewerActivity.MediaWrapper getEntries() {
        return new ViewerActivity.MediaWrapper(mMediaEntries, true);
    }

    public void start() {
        MainActivity act = (MainActivity) mContext.getActivity();
        act.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        act.mMediaCab = this;
        act.mToolbar.startActionMode(this);

        if (mMediaEntries.size() > 0) {
            for (MediaEntry e : mMediaEntries)
                ((MediaAdapter) mContext.getAdapter()).setItemChecked(e, true);
        }
    }

    public boolean isStarted() {
        return mActionMode != null;
    }

    private void invalidate() {
        if (mMediaEntries.size() == 0) finish();
        else if (mActionMode != null) mActionMode.invalidate();
    }

    public void finish() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
    }

    public void toggleEntry(MediaEntry p) {
        toggleEntry(p, false);
    }

    private void toggleEntry(MediaEntry p, boolean forceCheckOn) {
        boolean found = false;
        for (int i = 0; i < mMediaEntries.size(); i++) {
            if (mMediaEntries.get(i).data().equals(p.data())) {
                if (!forceCheckOn)
                    mMediaEntries.remove(i);
                found = true;
                break;
            }
        }
        if (!found)
            mMediaEntries.add(p);
        ((MediaAdapter) mContext.getAdapter()).setItemChecked(p, forceCheckOn || !found);
        invalidate();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mActionMode = mode;
        MainActivity act = (MainActivity) mContext.getActivity();
        if (act != null) {
            act.invalidateStatusColor();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final int darkGray = act.getResources().getColor(R.color.dark_theme_gray_lighter);
                act.getWindow().setStatusBarColor(darkGray);
                if (act.isColoredNavBar())
                    act.getWindow().setNavigationBarColor(darkGray);
            }
            mode.getMenuInflater().inflate(R.menu.cab, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        mode.setTitle(mMediaEntries.size() + "");
        boolean foundDir = false;
        boolean allAlbumsOrFolders = true;
        for (MediaEntry e : mMediaEntries) {
            if (!e.isAlbum() && !e.isFolder()) allAlbumsOrFolders = false;
            if (e.isFolder()) {
                foundDir = true;
                break;
            }
        }

        menu.findItem(R.id.share).setVisible(!foundDir);
        menu.findItem(R.id.exclude).setVisible(allAlbumsOrFolders);
        if (mMediaEntries.size() > 0) {
            MediaEntry firstEntry = mMediaEntries.get(0);
            boolean canShow = mMediaEntries.size() == 1 && !firstEntry.isVideo() && !firstEntry.isAlbum();
            menu.findItem(R.id.edit).setVisible(canShow);
            menu.findItem(R.id.details).setVisible(canShow);
        } else {
            menu.findItem(R.id.edit).setVisible(false);
            menu.findItem(R.id.details).setVisible(false);
        }

        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share:
                shareEntries();
                return true;
            case R.id.exclude:
                excludeEntries();
                return true;
            case R.id.delete:
                new MaterialDialog.Builder(mContext.getActivity())
                        .content(R.string.delete_bulk_confirm)
                        .positiveText(R.string.yes)
                        .negativeText(R.string.no)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog materialDialog) {
                                deleteEntries();
                            }

                            @Override
                            public void onNegative(MaterialDialog materialDialog) {
                            }
                        }).build().show();
                return true;
            case R.id.selectAll:
                selectAll();
                return true;
            case R.id.edit:
                try {
                    Uri uri = Uri.fromFile(new File(mMediaEntries.get(0).data()));
                    mContext.startActivity(new Intent(Intent.ACTION_EDIT)
                            .setDataAndType(uri, "image/*"));
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            case R.id.copyTo:
                mContext.startActivityForResult(new Intent(mContext.getActivity(), MainActivity.class)
                        .setAction(MainActivity.ACTION_SELECT_ALBUM)
                        .putExtra("mode", R.id.copyTo), COPY_REQUEST_CODE);
                return true;
            case R.id.moveTo:
                mContext.startActivityForResult(new Intent(mContext.getActivity(), MainActivity.class)
                        .setAction(MainActivity.ACTION_SELECT_ALBUM)
                        .putExtra("mode", R.id.moveTo), MOVE_REQUEST_CODE);
                return true;
            case R.id.details:
                final MediaEntry entry = mMediaEntries.get(0);
                final File file = new File(entry.data());
                Calendar cal = new GregorianCalendar();
                cal.setTimeInMillis(entry.dateTaken());
                new MaterialDialog.Builder(mContext.getActivity())
                        .title(R.string.details)
                        .content(Html.fromHtml(mContext.getString(R.string.details_contents,
                                TimeUtils.toStringLong(cal),
                                entry.width() + " x " + entry.height(),
                                file.getName(),
                                Utils.readableFileSize(file.length()),
                                file.getAbsolutePath())))
                        .contentLineSpacing(1.6f)
                        .positiveText(R.string.dismiss)
                        .show();
                return true;
        }
        return false;
    }

    private void excludeEntries() {
        new MaterialDialog.Builder(mContext.getActivity())
                .content(mMediaEntries.size() == 1 ?
                        R.string.exclude_prompt_single : R.string.exclude_prompt, mMediaEntries.size())
                .positiveText(R.string.yes)
                .negativeText(android.R.string.cancel)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        performExclude();
                    }
                })
                .show();
    }

    private void performExclude() {
        final ProgressDialog mDialog = new ProgressDialog(mContext.getActivity());
        mDialog.setMessage(mContext.getString(R.string.excluding));
        mDialog.setIndeterminate(false);
        mDialog.setMax(mMediaEntries.size());
        mDialog.setCancelable(true);
        mDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (MediaEntry e : mMediaEntries) {
                    if (!mDialog.isShowing()) break;
                    ExcludedFolderProvider.add(mContext.getActivity(), e.data());
                    mDialog.setProgress(mDialog.getProgress() + 1);
                }
                if (mContext.getActivity() != null) {
                    mContext.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDialog.dismiss();
                            finish();
                            mContext.reload();
                            ((MainActivity) mContext.getActivity()).reloadNavDrawerAlbums();
                        }
                    });
                }
            }
        }).start();
    }

    private void shareEntries() {
        List<MediaEntry> toSend = new ArrayList<>();
        for (MediaEntry e : mMediaEntries) {
            if (e.isAlbum()) {
                AlbumEntry album = (AlbumEntry) e;
                Collections.addAll(toSend, album.getContents(mContext.getActivity(),
                        album.bucketId() == AlbumEntry.ALBUM_ID_USEPATH));
            } else {
                toSend.add(e);
            }
        }
        if (toSend.size() > 0) {
            if (toSend.size() == 1) {
                String mime = toSend.get(0).isVideo() ? "video/*" : "image/*";
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND)
                            .setType(mime)
                            .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(toSend.get(0).data())));
                    mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.share_using)));
                } catch (ActivityNotFoundException e) {
                    if (mContext.getActivity() != null)
                        Toast.makeText(mContext.getActivity(), R.string.no_app_complete_action, Toast.LENGTH_SHORT).show();
                }
            } else {
                ArrayList<Uri> uris = new ArrayList<>();
                boolean foundPhotos = false;
                boolean foundVideos = false;
                for (MediaEntry p : toSend) {
                    foundPhotos = foundPhotos || !p.isVideo();
                    foundVideos = foundVideos || p.isVideo();
                    uris.add(Uri.fromFile(new File(p.data())));
                }
                String mime = "*/*";
                if (foundPhotos && !foundVideos) {
                    mime = "image/*";
                } else if (foundVideos && !foundPhotos) {
                    mime = "video/*";
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType(mime)
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.share_using)));
                } catch (ActivityNotFoundException e) {
                    if (mContext.getActivity() != null)
                        Toast.makeText(mContext.getActivity(), R.string.no_app_complete_action, Toast.LENGTH_SHORT).show();
                }
            }
        }
        finish();
    }

    public void finishCopyMove(File dest, int requestCode) {
        copyEntries(dest, requestCode == MOVE_REQUEST_CODE);
    }

    private void deleteEntries() {
        final List<MediaEntry> toDelete = new ArrayList<>();
        for (MediaEntry e : mMediaEntries) {
            if (e.isAlbum()) {
                AlbumEntry album = (AlbumEntry) e;
                Collections.addAll(toDelete, album.getContents(mContext.getActivity(),
                        album.bucketId() == AlbumEntry.ALBUM_ID_USEPATH));
            } else {
                toDelete.add(e);
            }
        }
        if (toDelete.size() == 0) {
            finish();
            return;
        }

        final ProgressDialog mDialog = new ProgressDialog(mContext.getActivity());
        mDialog.setMessage(mContext.getString(R.string.deleting));
        mDialog.setIndeterminate(false);
        mDialog.setMax(toDelete.size());
        mDialog.setCancelable(true);
        mDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (MediaEntry p : toDelete) {
                    if (!mDialog.isShowing()) break;
                    p.delete(mContext.getActivity());
                    mDialog.setProgress(mDialog.getProgress() + 1);
                }
                if (mContext.getActivity() != null) {
                    mContext.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDialog.dismiss();
                            finish();
                            mContext.reload();
                            ((MainActivity) mContext.getActivity()).reloadNavDrawerAlbums();
                        }
                    });
                }
            }
        }).start();
    }

    private void performCopy(Context context, MediaEntry src, File dst, boolean deleteAfter) throws IOException {
        dst = checkDuplicate(dst);
        InputStream in = new FileInputStream(src.data());
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();

        ContentResolver r = context.getContentResolver();
        ContentValues values = new ContentValues();
        if (deleteAfter) {
            if (src.isVideo()) {
                values.put(MediaStore.Video.VideoColumns.DATA, dst.getAbsolutePath());
                r.update(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                        MediaStore.Video.VideoColumns.DATA + " = ?", new String[]{src.data()});
            } else {
                values.put(MediaStore.Images.ImageColumns.DATA, dst.getAbsolutePath());
                r.update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values,
                        MediaStore.Images.ImageColumns.DATA + " = ?", new String[]{src.data()});
            }
            new File(src.data()).delete();
        } else {
            Log.i("UpdateMediaDatabase", "Scanning " + dst.getPath());
            MediaScannerConnection.scanFile(context,
                    new String[]{dst.getPath()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("Scanner", "Scanned " + path + ":");
                            Log.i("Scanner", "-> uri=" + uri);
                        }
                    });
        }
    }

    private String getNameNoExtension(File file) {
        if (file.isDirectory()) return file.getName();
        String name = file.getName();
        if (name.startsWith(".") || !name.substring(1).contains(".")) return name;
        return name.substring(0, name.lastIndexOf('.'));
    }

    private File checkDuplicate(File newFile) {
        if (!newFile.exists()) return newFile;
        final String parent = newFile.getParent();
        final String name = getNameNoExtension(newFile);
        final String extension = Utils.getExtension(newFile.getName());
        int index = 1;
        while (newFile.exists()) {
            newFile = new File(parent + File.separator + name + " (" + index + ")." + extension);
            index++;
        }
        return newFile;
    }

    private void copyEntries(final File destDir, final boolean deleteAfter) {
        final List<MediaEntry> toMove = new ArrayList<>();
        for (MediaEntry e : mMediaEntries) {
            if (e.isAlbum()) {
                AlbumEntry album = (AlbumEntry) e;
                Collections.addAll(toMove, album.getContents(mContext.getActivity(),
                        album.bucketId() == AlbumEntry.ALBUM_ID_USEPATH));
            } else {
                toMove.add(e);
            }
        }
        if (toMove.size() == 0) {
            finish();
            return;
        }

        final ProgressDialog mDialog = new ProgressDialog(mContext.getActivity());
        mDialog.setMessage(mContext.getString(deleteAfter ? R.string.moving : R.string.copying));
        mDialog.setIndeterminate(false);
        mDialog.setMax(toMove.size());
        mDialog.setCancelable(true);
        mDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (MediaEntry p : toMove) {
                    if (!mDialog.isShowing() || mContext == null || mContext.getActivity() == null)
                        break;
                    final File fi = new File(p.data());
                    final File newFi = new File(destDir, fi.getName());
                    try {
                        performCopy(mContext.getActivity(), p, newFi, deleteAfter);
                    } catch (final IOException e) {
                        e.printStackTrace();
                        if (mContext.getActivity() != null) {
                            mContext.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(mContext.getActivity(), e);
                                }
                            });
                        }
                        break;
                    }
                    mDialog.setProgress(mDialog.getProgress() + 1);
                }
                if (mContext.getActivity() != null) {
                    mContext.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity act = (MainActivity) mContext.getActivity();
                            if (act != null) {
                                act.notifyFoldersChanged();
                                act.reloadNavDrawerAlbums();
                            }
                            mDialog.dismiss();
                            finish();
                        }
                    });
                }
            }
        }).start();
    }

    private void selectAll() {
        List<MediaEntry> adapterPics = ((MediaAdapter) mContext.getAdapter()).getMedia().getMedia();
        for (MediaEntry p : adapterPics)
            toggleEntry(p, true);
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        if (mContext != null && mContext.getActivity() != null) {
            mContext.mCab = null;
            MainActivity act = (MainActivity) mContext.getActivity();
            act.mMediaCab = null;
            act.invalidateStatusColor();
            act.mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final int oldColor = act.primaryColorDark();
                act.getWindow().setStatusBarColor(act.getResources().getColor(android.R.color.transparent));
                if (act.isColoredNavBar())
                    act.getWindow().setNavigationBarColor(oldColor);
            }
            ((MediaAdapter) mContext.getAdapter()).clearChecked();
        }
        mMediaEntries.clear();
        mActionMode = null;
    }
}