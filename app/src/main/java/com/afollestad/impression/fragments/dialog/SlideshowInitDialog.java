package com.afollestad.impression.fragments.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author Aidan Follestad (afollestad)
 */
public class SlideshowInitDialog extends DialogFragment {

    private SlideshowCallback mCallback;
    private SeekBar mSeek;
    private CheckBox mLoop;
    private TextView mSeekLabel;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallback = (SlideshowCallback) activity;
    }

    public interface SlideshowCallback {
        void onStartSlideshow(long delay, boolean loop);
    }

    public SlideshowInitDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.slideshow)
                .customView(R.layout.dialog_slideshowinit, true)
                .callback(mActionCallback)
                .autoDismiss(false)
                .positiveText(R.string.start)
                .negativeText(android.R.string.cancel)
                .build();
        mSeek = (SeekBar) dialog.getCustomView().findViewById(R.id.seek);
        mSeekLabel = (TextView) dialog.getCustomView().findViewById(R.id.seekbarLabel);
        mSeek.setMax(9);
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    mSeekLabel.setText(getString(R.string.slideshow_seeklabel_single));
                } else {
                    mSeekLabel.setText(getString(R.string.slideshow_seeklabel, progress + 1));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mSeek.setProgress(2);
        mSeekLabel.setText(getString(R.string.slideshow_seeklabel, mSeek.getProgress() + 1));
        mLoop = (CheckBox) dialog.getCustomView().findViewById(R.id.loop);
        return dialog;
    }

    private final MaterialDialog.ButtonCallback mActionCallback = new MaterialDialog.ButtonCallback() {
        @Override
        public void onPositive(MaterialDialog dialog) {
            super.onPositive(dialog);
            dialog.dismiss();
            mCallback.onStartSlideshow(((long) mSeek.getProgress() + 1) * 1000, mLoop.isChecked());
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
}
