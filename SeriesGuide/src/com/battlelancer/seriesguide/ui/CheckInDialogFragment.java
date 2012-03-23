
package com.battlelancer.seriesguide.ui;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.getglueapi.GetGlue;
import com.battlelancer.seriesguide.getglueapi.GetGlue.CheckInTask;
import com.battlelancer.seriesguide.getglueapi.PrepareRequestTokenActivity;
import com.battlelancer.seriesguide.util.ShareUtils;
import com.battlelancer.seriesguide.util.ShareUtils.ProgressDialog;
import com.battlelancer.seriesguide.util.ShareUtils.ShareItems;
import com.battlelancer.seriesguide.util.ShareUtils.TraktCredentialsDialogFragment;
import com.battlelancer.seriesguide.util.ShareUtils.TraktTask;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;

public class CheckInDialogFragment extends SherlockDialogFragment {

    public static CheckInDialogFragment newInstance(String imdbid, int tvdbid, int season,
            int episode, String defaultMessage) {
        CheckInDialogFragment f = new CheckInDialogFragment();
        Bundle args = new Bundle();
        args.putString(ShareItems.IMDBID, imdbid);
        args.putInt(ShareItems.TVDBID, tvdbid);
        args.putInt(ShareItems.SEASON, season);
        args.putInt(ShareItems.EPISODE, episode);
        args.putString(ShareItems.SHARESTRING, defaultMessage);
        f.setArguments(args);
        return f;
    }

    protected boolean mGetGlueChecked;

    protected boolean mTraktChecked;

    private CompoundButton mToggleTraktButton;

    private CompoundButton mToggleGetGlueButton;

    private EditText mMessageBox;

    private View mCheckinButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_Sherlock_Dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().setTitle(R.string.checkin);
        final View layout = inflater.inflate(R.layout.checkin_dialog, null);
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getSherlockActivity());

        // some required values
        final String imdbid = getArguments().getString(ShareItems.IMDBID);
        final int tvdbid = getArguments().getInt(ShareItems.TVDBID);
        final int season = getArguments().getInt(ShareItems.SEASON);
        final int episode = getArguments().getInt(ShareItems.EPISODE);

        // get share service enabled settings
        mGetGlueChecked = prefs.getBoolean(SeriesGuidePreferences.KEY_SHAREWITHTRAKT, false);
        mTraktChecked = prefs.getBoolean(SeriesGuidePreferences.KEY_SHAREWITHTRAKT, false);

        // Message box
        mMessageBox = (EditText) layout.findViewById(R.id.message);

        // Paste episode button
        final String episodeText = getArguments().getString(ShareItems.SHARESTRING);
        layout.findViewById(R.id.pasteEpisode).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int start = mMessageBox.getSelectionStart();
                int end = mMessageBox.getSelectionEnd();
                mMessageBox.getText().replace(Math.min(start, end), Math.max(start, end),
                        episodeText, 0, episodeText.length());
            }
        });

        // GetGlue toggle
        mToggleGetGlueButton = (CompoundButton) layout.findViewById(R.id.toggleGetGlue);
        mToggleGetGlueButton.setChecked(mGetGlueChecked);
        mToggleGetGlueButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!GetGlue.isAuthenticated(prefs)) {
                        // authenticate already here
                        Intent i = new Intent(getSherlockActivity(),
                                PrepareRequestTokenActivity.class);
                        startActivity(i);
                    }
                }

                mGetGlueChecked = isChecked;
                updateCheckInButtonState();
            }
        });

        // Trakt toggle
        mToggleTraktButton = (CompoundButton) layout.findViewById(R.id.toggleTrakt);
        mToggleTraktButton.setChecked(mTraktChecked);
        mToggleTraktButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!ShareUtils.isTraktCredentialsValid(getSherlockActivity())) {
                        // authenticate already here
                        TraktCredentialsDialogFragment newFragment = TraktCredentialsDialogFragment
                                .newInstance();
                        newFragment.show(getFragmentManager(), "traktdialog");
                    }
                }

                mTraktChecked = isChecked;
                updateCheckInButtonState();
            }
        });

        // Checkin Button
        mCheckinButton = layout.findViewById(R.id.checkinButton);
        updateCheckInButtonState();
        mCheckinButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final String message = mMessageBox.getText().toString();

                if (mGetGlueChecked) {
                    if (!GetGlue.isAuthenticated(prefs)) {
                        // cancel if required auth data is missing
                        mToggleGetGlueButton.setChecked(false);
                        v.setEnabled(false);
                        return;
                    } else {
                        // check in
                        new CheckInTask(imdbid, message, getSherlockActivity()).execute();
                    }
                }

                if (mTraktChecked) {
                    if (!ShareUtils.isTraktCredentialsValid(getSherlockActivity())) {
                        // cancel if required auth data is missing
                        mToggleTraktButton.setChecked(false);
                        v.setEnabled(false);
                        return;
                    } else {
                        // check in

                        // We want to remove any currently showing
                        // dialog, so make our own transaction and
                        // take care of that here.
                        FragmentTransaction ft = getFragmentManager().beginTransaction();
                        Fragment prev = getFragmentManager().findFragmentByTag("progress-dialog");
                        if (prev != null) {
                            ft.remove(prev);
                        }
                        ProgressDialog newFragment = ProgressDialog.newInstance();
                        newFragment.show(ft, "progress-dialog");

                        // start the trakt check in task
                        new TraktTask(getSherlockActivity(), getFragmentManager(), null).checkin(
                                tvdbid, season, episode, message).execute();
                    }
                }

                // save service enabled setting
                prefs.edit().putBoolean(SeriesGuidePreferences.KEY_SHAREWITHTRAKT, mTraktChecked)
                        .putBoolean(SeriesGuidePreferences.KEY_SHAREWITHGETGLUE, mGetGlueChecked)
                        .commit();

                dismiss();
            }
        });

        return layout;
    }

    private void updateCheckInButtonState() {
        if (mGetGlueChecked || mTraktChecked) {
            mCheckinButton.setEnabled(true);
        } else {
            mCheckinButton.setEnabled(false);
        }
    }

}
