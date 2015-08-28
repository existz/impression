package com.afollestad.impression.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.impression.api.AlbumEntry;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class BreadCrumbLayout extends HorizontalScrollView implements View.OnClickListener {

    public static class Crumb implements Serializable {

        public Crumb(Context context, String path) {
            mContext = context;
            mPath = path;
        }

        private transient Context mContext;
        private final String mPath;
        private int mScrollY;
        private int mScrollOffset;

        public int getScrollY() {
            return mScrollY;
        }

        public int getScrollOffset() {
            return mScrollOffset;
        }

        public void setScrollY(int scrollY) {
            this.mScrollY = scrollY;
        }

        public void setScrollOffset(int scrollOffset) {
            this.mScrollOffset = scrollOffset;
        }

        public String getTitle() {
            if (mPath.equals("/"))
                return mContext.getString(R.string.root);
            else if (mPath.equals(Environment.getExternalStorageDirectory().getAbsolutePath()))
                return mContext.getString(R.string.internal_storage);
            return new java.io.File(mPath).getName();
        }

        public String getPath() {
            return mPath;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Crumb) && ((Crumb) o).getPath().equals(getPath());
        }

        @Override
        public String toString() {
            return getPath();
        }
    }

    public interface SelectionCallback {
        void onCrumbSelection(Crumb crumb, int count, int index);

        void onArtificialSelection(Crumb crumb, String file, boolean backStack);
    }

    public BreadCrumbLayout(Context context) {
        super(context);
        init();
    }

    public BreadCrumbLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BreadCrumbLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private List<Crumb> mCrumbs;
    private List<Crumb> mOldCrumbs;
    private LinearLayout mChildFrame;
    private int mActive;
    private SelectionCallback mCallback;
    private FragmentManager mFragmentManager;

    private void init() {
        setMinimumHeight((int) getResources().getDimension(R.dimen.breadcrumb_height));
        setClipToPadding(false);
        mCrumbs = new ArrayList<>();
        mChildFrame = new LinearLayout(getContext());
        addView(mChildFrame, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAlpha(View view, int alpha) {
        if (view instanceof ImageView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ((ImageView) view).setImageAlpha(alpha);
        } else {
            ViewCompat.setAlpha(view, alpha);
        }
    }

    public void setFragmentManager(FragmentManager fm) {
        this.mFragmentManager = fm;
    }

    public void addCrumb(@NonNull Crumb crumb, boolean refreshLayout) {
        LinearLayout view = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.bread_crumb, this, false);
        view.setTag(mCrumbs.size());
        view.setOnClickListener(this);

        ImageView iv = (ImageView) view.getChildAt(1);
        Drawable arrow = ContextCompat.getDrawable(getContext(), R.drawable.ic_right_arrow);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            arrow.setAutoMirrored(true);
        iv.setImageDrawable(arrow);
        iv.setVisibility(View.GONE);

        mChildFrame.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mCrumbs.add(crumb);
        if (refreshLayout) {
            mActive = mCrumbs.size() - 1;
            requestLayout();
        }
        invalidateActivatedAll();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        //RTL works fine like this
        View child = mChildFrame.getChildAt(mActive);
        if (child != null)
            smoothScrollTo(child.getLeft(), 0);
    }

    public Crumb findCrumb(@NonNull String forDir) {
        for (int i = 0; i < mCrumbs.size(); i++) {
            if (mCrumbs.get(i).getPath().equals(forDir))
                return mCrumbs.get(i);
        }
        return null;
    }

    public void clearCrumbs() {
        try {
            mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            mOldCrumbs = new ArrayList<>(mCrumbs);
            mCrumbs.clear();
            mChildFrame.removeAllViews();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public Crumb getCrumb(int index) {
        return mCrumbs.get(index);
    }

    public boolean canPop() {
        return mFragmentManager.getBackStackEntryCount() > 0 && getActiveIndex() > 0 &&
                getActiveIndex() <= (mCrumbs.size() - 1);
    }

    public void pop() {
        try {
            if (mFragmentManager.findFragmentByTag("[search]") != null)
                mFragmentManager.popBackStack();
            mFragmentManager.popBackStack();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        setActive(mCrumbs.get(getActiveIndex() - 1));
    }

    public void setCallback(SelectionCallback callback) {
        mCallback = callback;
    }

    public boolean setActive(Crumb newActive) {
        mActive = mCrumbs.indexOf(newActive);
        invalidateActivatedAll();
        boolean success = mActive > -1;
        if (success)
            requestLayout();
        return success;
    }

    private void invalidateActivatedAll() {
        for (int i = 0; i < mCrumbs.size(); i++) {
            Crumb crumb = mCrumbs.get(i);
            invalidateActivated(mChildFrame.getChildAt(i), mActive == mCrumbs.indexOf(crumb), false, i < mCrumbs.size() - 1)
                    .setText(crumb.getTitle());
        }
    }

    private void removeCrumbAt(int index) {
        mCrumbs.remove(index);
        mChildFrame.removeViewAt(index);
    }

    private void updateIndices() {
        for (int i = 0; i < mChildFrame.getChildCount(); i++)
            mChildFrame.getChildAt(i).setTag(i);
    }

    private boolean isStorage(String path) {
        return path == null ||
                path.equals(AlbumEntry.ALBUM_OVERVIEW) ||
                path.equals(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    public void setActiveOrAdd(@NonNull Crumb crumb, boolean truncatePath, boolean forceRecreate) {
        if (forceRecreate || !setActive(crumb)) {
            clearCrumbs();
            final List<String> newPathSet = new ArrayList<>();

            File p = new File(crumb.getPath());
            newPathSet.add(p.getAbsolutePath());
            if (!isStorage(p.getAbsolutePath())) {
                while ((p = p.getParentFile()) != null) {
                    newPathSet.add(0, p.getAbsolutePath());
                    if (isStorage(p.getPath()))
                        break;
                }
            }

            if (truncatePath && newPathSet.size() > 2) {
                for (int i = 1; i < newPathSet.size() - 1; i++)
                    newPathSet.remove(i);
            }

            for (int index = 0; index < newPathSet.size(); index++) {
                final String fi = newPathSet.get(index);
                crumb = new Crumb(getContext(), fi);

                // Restore scroll positions saved before clearing
                for (Iterator<Crumb> iterator = mOldCrumbs.iterator(); iterator.hasNext(); ) {
                    Crumb old = iterator.next();
                    if (old.equals(crumb)) {
                        crumb.setScrollY(old.getScrollY());
                        crumb.setScrollOffset(old.getScrollOffset());
                        iterator.remove(); // minimize number of linear passes by removing un-used crumbs from history
                        break;
                    }
                }

                mCallback.onArtificialSelection(crumb, crumb.getPath(), true);
                addCrumb(crumb, true);
            }

            // History no longer needed
            mOldCrumbs.clear();
        } else {
            if (isStorage(crumb.getPath())) {
                Crumb c = mCrumbs.get(0);
                while (c != null && !isStorage(c.getPath())) {
                    removeCrumbAt(0);
                    if (mCrumbs.size() > 0)
                        c = mCrumbs.get(0);
                }
                updateIndices();
                requestLayout();
            }
            mCallback.onArtificialSelection(crumb, crumb.getPath(), true);
        }
    }

    public int size() {
        return mCrumbs.size();
    }

    private TextView invalidateActivated(View view, boolean isActive, boolean noArrowIfAlone, boolean allowArrowVisible) {
        LinearLayout child = (LinearLayout) view;
        TextView tv = (TextView) child.getChildAt(0);
        tv.setTextColor(ContextCompat.getColor(getContext(), isActive ? R.color.crumb_active : R.color.crumb_inactive));
        ImageView iv = (ImageView) child.getChildAt(1);
        setAlpha(iv, isActive ? 255 : 109);
        if (noArrowIfAlone && getChildCount() == 1)
            iv.setVisibility(View.GONE);
        else if (allowArrowVisible)
            iv.setVisibility(View.VISIBLE);
        return tv;
    }

    public int getActiveIndex() {
        return mActive;
    }

    @Override
    public void onClick(View v) {
        if (mCallback != null) {
            int index = (Integer) v.getTag();
            mCallback.onCrumbSelection(mCrumbs.get(index), mCrumbs.size(), index);
        }
    }


    public static class SavedStateWrapper implements Serializable {

        public final int mActive;
        public final List<Crumb> mCrumbs;
        public final int mVisibility;

        public SavedStateWrapper(BreadCrumbLayout view) {
            mActive = view.mActive;
            mCrumbs = view.mCrumbs;
            mVisibility = view.getVisibility();
        }
    }

    public SavedStateWrapper getStateWrapper() {
        return new SavedStateWrapper(this);
    }

    public void restoreFromStateWrapper(SavedStateWrapper mSavedState, Activity context) {
        if (mSavedState != null) {
            mActive = mSavedState.mActive;
            for (Crumb c : mSavedState.mCrumbs) {
                c.mContext = getContext();
                addCrumb(c, false);
            }
            requestLayout();
            setVisibility(mSavedState.mVisibility);
        }
    }
}