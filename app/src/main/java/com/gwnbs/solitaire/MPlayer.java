package com.gwnbs.solitaire;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;

import androidx.preference.PreferenceManager;

class MPlayer {

    public static void playTakingCard(Context context) {
        MediaPlayer mp = MediaPlayer.create(context, R.raw.taking_card);
        mp.start();
        mp.setOnCompletionListener(mp1 -> {
            mp1.reset();
            mp1.release();
        });
    }

    public static void playClicked(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean("Sound", true)) {
            MediaPlayer mp = MediaPlayer.create(context, R.raw.clicked);
            mp.start();
            mp.setOnCompletionListener(mp1 -> {
                mp1.reset();
                mp1.release();
            });
        }
    }
}
