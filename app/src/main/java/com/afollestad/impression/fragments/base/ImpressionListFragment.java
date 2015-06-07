package com.afollestad.impression.fragments.base;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.impression.ui.MainActivity;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class ImpressionListFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    RecyclerView.Adapter getAdapter() {
        return mAdapter;
    }

    RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    final void setListShown(boolean shown) {
        View v = getView();
        if (v == null || getActivity() == null) return;
        if (shown) {
            v.findViewById(R.id.list).setVisibility(mAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.empty).setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.progress).setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();
        } else {
            v.findViewById(R.id.list).setVisibility(View.GONE);
            v.findViewById(R.id.empty).setVisibility(View.GONE);
            v.findViewById(R.id.progress).setVisibility(View.VISIBLE);
        }
    }

    @Nullable
    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.empty)).setText(getEmptyText());
        mRecyclerView = (RecyclerView) view.findViewById(R.id.list);
        mRecyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        invalidateLayoutManagerAndAdapter();
    }

    protected void invalidateLayoutManagerAndAdapter() {
        mRecyclerView.setLayoutManager(getLayoutManager());
        mAdapter = initializeAdapter();
        mRecyclerView.setAdapter(mAdapter);
    }

    public void onBackStackResume() {
        if (getActivity() != null) {
            MainActivity act = (MainActivity) getActivity();
            act.mRecyclerView = mRecyclerView;
        }
    }

    protected abstract String getTitle();

    protected abstract int getEmptyText();

    protected abstract GridLayoutManager getLayoutManager();

    protected abstract RecyclerView.Adapter initializeAdapter();
}
