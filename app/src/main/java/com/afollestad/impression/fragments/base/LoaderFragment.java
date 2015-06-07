package com.afollestad.impression.fragments.base;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.afollestad.impression.App;
import com.afollestad.impression.R;
import com.afollestad.impression.accounts.base.Account;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.adapters.base.HybridCursorAdapter;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.providers.SortMemoryProvider;
import com.afollestad.impression.ui.MainActivity;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.views.BreadCrumbLayout;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class LoaderFragment<VH extends RecyclerView.ViewHolder>
        extends ImpressionListFragment implements Account.EntriesCallback {

    public boolean sortRememberDir = false;
    protected MediaAdapter.SortMode mSortCache;
    protected BreadCrumbLayout.Crumb crumb;

    public void saveScrollPosition() {
        if (crumb == null)
            return;
        crumb.setScrollY(getLayoutManager().findFirstVisibleItemPosition());
        final View firstChild = getRecyclerView().getChildAt(0);
        if (firstChild != null)
            crumb.setScrollOffset((int) firstChild.getY());
    }

    private void restoreScrollPosition() {
        if (crumb == null)
            return;
        final int scrollY = crumb.getScrollY();
        if (scrollY > -1 && scrollY < getAdapter().getItemCount()) {
            getLayoutManager().scrollToPositionWithOffset(scrollY, crumb.getScrollOffset());
        }
    }

    private int getOverviewMode() {
        if (getActivity() == null) return 1;
        return PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("overview_mode", 1);
    }

    protected final void setFilterMode(MediaAdapter.FileFilterMode mode) {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                .putInt("filter_mode", mode.value()).commit();
        reload();
        getActivity().invalidateOptionsMenu();
    }

    protected static MediaAdapter.FileFilterMode getFilterMode(Context context) {
        if (context == null) return MediaAdapter.FileFilterMode.ALL;
        int explorerMode = PreferenceManager.getDefaultSharedPreferences(context).getInt("filter_mode", 0);
        return MediaAdapter.FileFilterMode.valueOf(explorerMode);
    }

    protected final void setSortMode(MediaAdapter.SortMode mode, String rememberPath) {
        mSortCache = mode;
        SortMemoryProvider.remember(getActivity(), rememberPath, mode);
        invalidateLayoutManagerAndAdapter();
        reload();
        getActivity().invalidateOptionsMenu();
    }

    protected final void setViewMode(MediaAdapter.ViewMode mode) {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                .putInt("view_mode", mode.value()).commit();
        invalidateLayoutManagerAndAdapter();
        reload();
        getActivity().invalidateOptionsMenu();
    }

    protected final MediaAdapter.ViewMode getViewMode() {
        if (getActivity() == null) return MediaAdapter.ViewMode.GRID;
        int explorerMode = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt("view_mode", 0);
        return MediaAdapter.ViewMode.valueOf(explorerMode);
    }

    protected final void setExplorerMode(final boolean explorerMode) {
        MainActivity act = (MainActivity) getActivity();
        if (act == null) return;
        PreferenceManager.getDefaultSharedPreferences(act).edit()
                .putBoolean("explorer_mode", explorerMode).commit();
        reload();
        act.getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        act.setTitle(getTitle());
        act.invalidateOptionsMenu();
        act.invalidateCrumbs();
    }

    protected final boolean isExplorerMode() {
        return getActivity() != null && PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("explorer_mode", false);
    }

    protected final int getGridWidth() {
        if (getActivity() == null) return 1;
        final Resources r = getResources();
        final int defaultGrid = r.getInteger(R.integer.default_grid_width);
        final int orientation = r.getConfiguration().orientation;
        return PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getInt("grid_size_" + orientation, defaultGrid);
    }

    protected final void setGridWidth(int width) {
        if (getActivity() == null) return;
        final Resources r = getResources();
        final int orientation = r.getConfiguration().orientation;
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                .putInt("grid_size_" + orientation, width).commit();
        invalidateLayoutManagerAndAdapter();
        reload();
    }

    @Override
    public final HybridCursorAdapter<VH> getAdapter() {
        return (HybridCursorAdapter<VH>) super.getAdapter();
    }

    protected abstract String getAlbumPath();

    private void getAllEntries(final Account.EntriesCallback callback) {
        if (getActivity() == null) return;
        App.getCurrentAccount(getActivity(), new Account.AccountCallback() {
            @Override
            public void onAccount(Account acc) {
                if (!isAdded()) return;
                if (acc != null) {
                    acc.getEntries(getAlbumPath(), getOverviewMode(),
                            isExplorerMode(), getFilterMode(getActivity()),
                            SortMemoryProvider.remember(getActivity(), getAlbumPath()), callback);
                }
            }
        });
    }

    private void invalidateEmptyText() {
        MediaAdapter.FileFilterMode mode = getFilterMode(getActivity());
        View v = getView();
        if (v != null) {
            TextView empty = (TextView) v.findViewById(R.id.empty);
            if (empty != null) {
                switch (mode) {
                    default:
                        empty.setText(R.string.no_photosorvideos);
                        break;
                    case PHOTOS:
                        empty.setText(R.string.no_photos);
                        break;
                    case VIDEOS:
                        empty.setText(R.string.no_videos);
                        break;
                }
            }
        }
    }

    public final void reload() {
        final Activity act = getActivity();
        if (act == null) return;

        saveScrollPosition();
        invalidateEmptyText();
        setListShown(false);

        if (getAdapter() != null)
            getAdapter().clear();
        getAllEntries(this);
    }

    @Override
    protected abstract HybridCursorAdapter<VH> initializeAdapter();

    @Override
    public void onEntries(MediaEntry[] entries) {
        if (!isAdded())
            return;
        else if (getAdapter() != null)
            getAdapter().addAll(entries);
        setListShown(true);
        restoreScrollPosition();
        invalidateSubtitle(entries);
    }

    private void invalidateSubtitle(MediaEntry[] entries) {
        ActionBarActivity act = (ActionBarActivity) getActivity();
        if (act != null) {
            final boolean toolbarStats = PreferenceManager.getDefaultSharedPreferences(act)
                    .getBoolean("toolbar_album_stats", true);
            if (toolbarStats) {
                if (entries == null || entries.length == 0) {
                    act.getSupportActionBar().setSubtitle(getString(R.string.empty));
                    return;
                }

                int folderCount = 0;
                int albumCount = 0;
                int videoCount = 0;
                int photoCount = 0;
                for (MediaEntry e : entries) {
                    if (e.isFolder()) folderCount++;
                    else if (e.isAlbum()) albumCount++;
                    else if (e.isVideo()) videoCount++;
                    else photoCount++;
                }
                final StringBuilder sb = new StringBuilder();
                if (albumCount > 1) {
                    sb.append(getString(R.string.x_albums, albumCount));
                } else if (albumCount == 1) {
                    sb.append(getString(R.string.one_album));
                }
                if (folderCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_folders, folderCount));
                } else if (folderCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_folder));
                }
                if (photoCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_photos, photoCount));
                } else if (photoCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_photo));
                }
                if (videoCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_videos, videoCount));
                } else if (videoCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_video));
                }
                act.getSupportActionBar().setSubtitle(sb.toString());
            } else {
                act.getSupportActionBar().setSubtitle(null);
            }
        }
    }

    @Override
    public void onError(Exception e) {
        if (getActivity() == null) return;
        Utils.showErrorDialog(getActivity(), e);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getAdapter() != null)
            getAdapter().changeContent(null, null, true, false);
    }
}