package com.afollestad.impression.adapters;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.view.ViewGroup;

import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.fragments.viewer.ViewerPageFragment;
import com.afollestad.impression.utils.FragmentStatePagerAdapter;

import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ViewerPageAdapter extends FragmentStatePagerAdapter {

    private final List<MediaEntry> mMedia;
    private String mInfo;
    public int mCurrentPage;
    private ViewerPageFragment mCurrentFragment;

    public ViewerPageAdapter(FragmentManager fm, List<MediaEntry> media, String info, int initialOffset) {
        super(fm);
        mMedia = media;
        mInfo = info;
        mCurrentPage = initialOffset;
    }

    public void add(MediaEntry p) {
        mMedia.add(0, p);
        notifyDataSetChanged();
    }

    public void remove(int index) {
        mMedia.remove(index);
        notifyDataSetChanged();
    }

    private int translateToGridIndex(int local) {
        return mMedia.get(local).realIndex();
    }

    @Override
    public Fragment getItem(int position) {
        String info = null;
        if (mCurrentPage == position) {
            info = mInfo;
            mInfo = null;
        }
        int gridPosition = translateToGridIndex(position);
        return ViewerPageFragment.create(mMedia.get(position), gridPosition, info)
                .setIsActive(mCurrentPage == position);
    }

    @Override
    public int getCount() {
        return mMedia.size();
    }

    @Override
    public int getItemPosition(Object object) {
        return PagerAdapter.POSITION_NONE;
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        super.setPrimaryItem(container, position, object);
        mCurrentFragment = (ViewerPageFragment) object;
    }

    public ViewerPageFragment getCurrentDetailsFragment() {
        return mCurrentFragment;
    }
}