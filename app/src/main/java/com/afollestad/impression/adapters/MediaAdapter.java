package com.afollestad.impression.adapters;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.impression.adapters.base.HybridCursorAdapter;
import com.afollestad.impression.api.AlbumEntry;
import com.afollestad.impression.api.FolderEntry;
import com.afollestad.impression.api.PhotoEntry;
import com.afollestad.impression.api.VideoEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.ui.viewer.ViewerActivity;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.views.ImpressionImageView;
import com.afollestad.materialdialogs.util.TypefaceHelper;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MediaAdapter extends HybridCursorAdapter<MediaAdapter.ViewHolder> implements View.OnClickListener, View.OnLongClickListener {

    private final Callback mCallback;
    private final List<MediaEntry> mEntries;
    private final List<String> mCheckedPaths;
    private final Typeface mRobotoLight;
    private final Typeface mRobotoRegular;
    private final SortMode mSortMode;
    private final ViewMode mViewMode;
    private final Context mContext;
    private final boolean mSelectAlbumMode;

    private final int defaultImageBackground;
    private final int emptyImageBackground;

    public interface Callback {
        void onClick(int index, View view, MediaEntry pic, boolean longClick);
    }

    public enum FileFilterMode {
        ALL(0),
        PHOTOS(1),
        VIDEOS(2);

        private final int value;

        FileFilterMode(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static FileFilterMode valueOf(int value) {
            switch (value) {
                default:
                    return ALL;
                case 1:
                    return PHOTOS;
                case 2:
                    return VIDEOS;
            }
        }
    }

    public enum ViewMode {
        GRID(0),
        LIST(1);

        private final int value;

        ViewMode(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static ViewMode valueOf(int value) {
            switch (value) {
                default:
                    return GRID;
                case 1:
                    return LIST;
            }
        }
    }

    public enum SortMode {
        NAME_ASC(0),
        NAME_DESC(1),
        MODIFIED_DATE_ASC(2),
        MODIFIED_DATE_DESC(3);

        private final int value;

        SortMode(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        public static SortMode valueOf(int value) {
            switch (value) {
                default:
                    return NAME_ASC;
                case 1:
                    return NAME_DESC;
                case 2:
                    return MODIFIED_DATE_ASC;
                case 3:
                    return MODIFIED_DATE_DESC;
            }
        }

        public static final int DEFAULT = 2;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final View view;
        public final ImpressionImageView image;
        public final View imageProgress;
        public final View titleFrame;
        public final TextView title;
        public final TextView subTitle;

        public ViewHolder(View v) {
            super(v);
            view = v;
            image = (ImpressionImageView) v.findViewById(R.id.image);
            imageProgress = v.findViewById(R.id.imageProgress);
            titleFrame = v.findViewById(R.id.titleFrame);
            title = (TextView) v.findViewById(R.id.title);
            subTitle = (TextView) v.findViewById(R.id.subTitle);
        }
    }

    public static class MediaNameSorter implements Comparator<MediaEntry> {

        private final boolean desc;

        public MediaNameSorter(boolean desc) {
            this.desc = desc;
        }

        @Override
        public int compare(MediaEntry lhs, MediaEntry rhs) {
            String right = rhs.displayName();
            String left = lhs.displayName();
            if (right == null) right = "";
            if (left == null) left = "";
            if (desc) {
                return right.compareTo(left);
            } else {
                return left.compareTo(right);
            }
        }
    }

    public static class MediaModifiedSorter implements Comparator<MediaEntry> {

        private final boolean desc;

        public MediaModifiedSorter(boolean desc) {
            this.desc = desc;
        }

        @Override
        public int compare(MediaEntry lhs, MediaEntry rhs) {
            Long right;
            Long left;
            if (rhs != null) right = rhs.dateModified();
            else right = 0l;
            if (lhs != null) left = lhs.dateModified();
            else left = 0l;

            if (desc) {
                return left.compareTo(right);
            } else {
                return right.compareTo(left);
            }
        }
    }

    public MediaAdapter(Context context, SortMode sort, ViewMode viewMode, Callback callback, boolean selectAlbumMode) {
        mContext = context;
        mSortMode = sort;
        mViewMode = viewMode;
        mCallback = callback;
        mEntries = new ArrayList<>();
        mCheckedPaths = new ArrayList<>();
        mSelectAlbumMode = selectAlbumMode;

        mRobotoLight = TypefaceHelper.get(mContext, "Roboto-Light");
        mRobotoRegular = TypefaceHelper.get(mContext, "Roboto-Regular");

        defaultImageBackground = Utils.resolveColor(context, R.attr.default_image_background);
        emptyImageBackground = Utils.resolveColor(context, R.attr.empty_image_background);
    }

    @Override
    public int getItemViewType(int position) {
        if (mEntries.get(position).isFolder())
            return 1;
        return 0;
    }

    public void setItemChecked(MediaEntry entry, boolean checked) {
        if (checked) {
            if (!mCheckedPaths.contains(entry.data()))
                mCheckedPaths.add(entry.data());
            for (int i = 0; i < mEntries.size(); i++) {
                if (mEntries.get(i).data() != null &&
                        mEntries.get(i).data().equals(entry.data())) {
                    notifyItemChanged(i);
                    break;
                }
            }
        } else {
            if (mCheckedPaths.contains(entry.data()))
                mCheckedPaths.remove(entry.data());
            for (int i = 0; i < mEntries.size(); i++) {
                if (mEntries.get(i).data().equals(entry.data())) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    public void clearChecked() {
        mCheckedPaths.clear();
        notifyDataSetChanged();
    }

    public ViewerActivity.MediaWrapper getMedia() {
        return new ViewerActivity.MediaWrapper(mEntries, false);
    }

    @Override
    public void onClick(View v) {
        if (mCallback != null) {
            Integer index = (Integer) v.getTag();
            mCallback.onClick(index, v, mEntries.get(index), false);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (mCallback != null) {
            Integer index = (Integer) v.getTag();
            mCallback.onClick(index, v, mEntries.get(index), true);
        }
        return true;
    }

    @Override
    public void clear() {
        mEntries.clear();
    }

    private void add(MediaEntry e) {
        if (e instanceof AlbumEntry) {
            synchronized (mEntries) {
                boolean found = false;
                for (int i = 0; i < mEntries.size(); i++) {
                    MediaEntry e2 = mEntries.get(i);
                    if (e2.data().equals(e.data())) {
                        found = true;
                        mEntries.set(i, e);
                        break;
                    }
                }
                if (!found)
                    mEntries.add(e);
            }
        } else {
            mEntries.add(e);
        }
    }

    @Override
    public void addAll(MediaEntry[] entries) {
        if (entries != null) {
            for (MediaEntry e : entries)
                add(e);
        }
        if (mEntries.size() > 0)
            Collections.sort(mEntries, getSorter());
        notifyDataSetChanged();
    }

    private Comparator<MediaEntry> getSorter() {
        switch (mSortMode) {
            default:
                return new MediaNameSorter(false);
            case NAME_DESC:
                return new MediaNameSorter(true);
            case MODIFIED_DATE_ASC:
                return new MediaModifiedSorter(false);
            case MODIFIED_DATE_DESC:
                return new MediaModifiedSorter(true);
        }
    }

    @Override
    public void changeContent(Cursor cursor, Uri from, boolean clear, boolean explorerMode) {
        if (cursor == null || from == null) {
            mEntries.clear();
            return;
        }
        if (clear) mEntries.clear();
        final boolean photos = from.toString().equals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString());
        while (cursor.moveToNext()) {
            MediaEntry pic = (photos ? new PhotoEntry().load(cursor) : new VideoEntry().load(cursor));
            mEntries.add(pic);
        }
        cursor.close();
        Collections.sort(mEntries, getSorter());
    }

    @Override
    public void changeContent(File[] content, boolean explorerMode, FileFilterMode mode) {
        mEntries.clear();
        if (content == null || content.length == 0) return;
        for (File fi : content) {
            if (!fi.isHidden()) {
                if (fi.isDirectory()) {
                    if (explorerMode)
                        mEntries.add(new FolderEntry(fi));
                } else {
                    String mime = Utils.getMimeType(Utils.getExtension(fi.getName()));
                    if (mime != null) {
                        if (mime.startsWith("image/") && mode != FileFilterMode.VIDEOS) {
                            mEntries.add(new PhotoEntry().load(fi));
                        } else if (mime.startsWith("video/") && mode != FileFilterMode.PHOTOS) {
                            mEntries.add(new VideoEntry().load(fi));
                        }
                    }
                }
            }
        }
        Collections.sort(mEntries, getSorter());
    }

    @Override
    public MediaAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        if (viewType == 1) {
            v = LayoutInflater.from(mContext).inflate(mViewMode == ViewMode.GRID ?
                    R.layout.grid_item_entry_folder : R.layout.list_item_entry, parent, false);
        } else {
            v = LayoutInflater.from(mContext).inflate(mViewMode == ViewMode.GRID ?
                    R.layout.grid_item_entry : R.layout.list_item_entry, parent, false);
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        MediaEntry entry = mEntries.get(position);

        if (!mSelectAlbumMode || (entry.isFolder() || entry.isAlbum())) {
            holder.view.setActivated(mCheckedPaths.contains(entry.data()));
            holder.view.setTag(position);
            holder.view.setOnClickListener(this);
            if (!mSelectAlbumMode)
                holder.view.setOnLongClickListener(this);
            holder.image.setBackgroundColor(defaultImageBackground);
            ViewCompat.setTransitionName(holder.image, "view_" + position);
        } else {
            if (holder.view instanceof FrameLayout)
                ((FrameLayout) holder.view).setForeground(null);
            else holder.view.setBackground(null);
        }

        holder.title.setTypeface(mRobotoRegular);
        if (holder.subTitle != null)
            holder.subTitle.setTypeface(mRobotoLight);

        if (entry.isAlbum()) {
            holder.titleFrame.setVisibility(View.VISIBLE);
            holder.title.setText(entry.displayName());
            if (((AlbumEntry) entry).mFirstPath == null) {
                holder.image.setBackgroundColor(emptyImageBackground);
                if (holder.subTitle != null)
                    holder.subTitle.setText("0");
            } else if (entry.size() == 1) {
                if (holder.subTitle != null)
                    holder.subTitle.setText("1");
            } else if (holder.subTitle != null) {
                holder.subTitle.setText("" + entry.size());
            }
            holder.image.load(entry, holder.imageProgress);
        } else if (entry.isFolder()) {
            holder.image.setBackground(null);
            holder.titleFrame.setVisibility(View.VISIBLE);
            holder.title.setText(entry.displayName());
            if (holder.imageProgress != null)
                holder.imageProgress.setVisibility(View.GONE);
            holder.image.setImageResource(R.drawable.ic_folder);

            if (mViewMode == ViewMode.LIST) {
                if (holder.subTitle != null) {
                    holder.subTitle.setVisibility(View.VISIBLE);
                    holder.subTitle.setText(R.string.folder);
                }
            } else if (holder.subTitle != null) {
                holder.subTitle.setVisibility(View.GONE);
            }
        } else {
            if (mViewMode == ViewMode.GRID) {
                holder.titleFrame.setVisibility(View.GONE);
            } else {
                holder.titleFrame.setVisibility(View.VISIBLE);
                holder.title.setText(entry.displayName());
                if (holder.subTitle != null) {
                    holder.subTitle.setVisibility(View.VISIBLE);
                    holder.subTitle.setText(entry.mimeType());
                }
            }
            Ion.getDefault(mContext).configure().setLogging("ION", Log.ERROR);
            holder.image.load(entry, holder.imageProgress);
        }
    }

    @Override
    public int getItemCount() {
        return mEntries.size();
    }
}