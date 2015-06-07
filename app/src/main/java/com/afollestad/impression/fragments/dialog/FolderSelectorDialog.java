package com.afollestad.impression.fragments.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;

import com.afollestad.impression.R;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public class FolderSelectorDialog extends DialogFragment implements MaterialDialog.ListCallback {

    private File parentFolder;
    private File[] parentContents;
    private boolean canGoUp = true;
    private FolderCallback mCallback;

    public interface FolderCallback {
        void onFolderSelection(File folder);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallback = (FolderCallback) activity;
    }

    public FolderSelectorDialog() {
        parentFolder = Environment.getExternalStorageDirectory();
        parentContents = listFiles();
    }

    private String[] getContentsArray() {
        String[] results = new String[parentContents.length + (canGoUp ? 1 : 0)];
        if (canGoUp) results[0] = getString(R.string.go_up);
        for (int i = 0; i < parentContents.length; i++)
            results[canGoUp ? i + 1 : i] = parentContents[i].getName();
        return results;
    }

    private File[] listFiles() {
        File[] contents = parentFolder.listFiles();
        List<File> results = new ArrayList<>();
        if (contents != null) {
            for (File fi : contents) {
                if (fi.isDirectory()) results.add(fi);
            }
            Collections.sort(results, new FolderSorter());
        }
        return results.toArray(new File[results.size()]);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialDialog.Builder(getActivity())
                .title(parentFolder.getAbsolutePath())
                .items(getContentsArray())
                .itemsCallback(this)
                .callback(mActionCallback)
                .autoDismiss(false)
                .positiveText(R.string.choose)
                .negativeText(android.R.string.cancel)
                .build();
    }

    @Override
    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence s) {
        if (canGoUp && i == 0) {
            parentFolder = parentFolder.getParentFile();
            canGoUp = parentFolder.getParent() != null;
        } else {
            parentFolder = parentContents[canGoUp ? i - 1 : i];
            canGoUp = true;
        }
        parentContents = listFiles();
        MaterialDialog dialog = (MaterialDialog) getDialog();
        dialog.setTitle(parentFolder.getAbsolutePath());
        dialog.setItems(getContentsArray());
    }

    private final MaterialDialog.ButtonCallback mActionCallback = new MaterialDialog.ButtonCallback() {
        @Override
        public void onPositive(MaterialDialog dialog) {
            super.onPositive(dialog);
            dialog.dismiss();
            mCallback.onFolderSelection(parentFolder);
        }

        @Override
        public void onNegative(MaterialDialog dialog) {
            super.onNegative(dialog);
            dialog.dismiss();
        }
    };

    public void show(Activity context) {
        show(context.getFragmentManager(), "FOLDER_SELECTOR");
    }

    private static class FolderSorter implements Comparator<File> {
        @Override
        public int compare(File lhs, File rhs) {
            String left;
            String right;
            if (lhs != null) left = lhs.getName();
            else left = "";
            if (rhs != null) right = rhs.getName();
            else right = "";
            return left.compareTo(right);
        }
    }
}
