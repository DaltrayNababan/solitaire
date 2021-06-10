/*
  Copyright 2008 Google Inc.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  
       http://www.apache.org/licenses/LICENSE-2.0
  
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

  Modified by Curtis Gedak 2015, 2016, 2017
  Modified by Daltray Nababan 2021
*/
package com.gwnbs.solitaire;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Calendar;

// Base activity class.
public class SolitaireCG extends Activity {

    public static String VERSION_NAME = "";
    // Workaround for inaccessible menu items on some devices
    // and Android versions - add extra blank menu items
    // View extracted from main.xml.
    private View mMainView;
    private SolitaireView mSolitaireView;
    private SharedPreferences mSettings, prefDay;
    public static InterstitialAd interstitialAd;
    private BottomSheetDialog dialogMenu;
    private RewardedAd rewardedAd;
    private TextView noLive;
    private TextView textCountTimer;
    private LinearLayout layoutLives;
    private int minutes, seconds;
    private String today, liveTime = "";

    // Shared preferences are where the various user settings are stored.
    public SharedPreferences GetSettings() {
        return mSettings;
    }

    // Methods to assist with tracking and maintaining state on device rotation
    private String mRestoreState = "";

    public void ClearRestoreState() {
        mRestoreState = "";
    }

    public String GetRestoreState() {
        return mRestoreState;
    }

    public void SetRestoreState(String state) {
        mRestoreState = state;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Recall last state before configuration/orientation change
        ConfigWrapper config = (ConfigWrapper) getLastNonConfigurationInstance();
        if (config != null) {
            SetRestoreState(config.screen);
        }

        // Get shared preferences
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        prefDay = getSharedPreferences("DAY", MODE_PRIVATE);
        minutes = GetSettings().getInt(Value.Minutes, 29);
        seconds = GetSettings().getInt(Value.Seconds, 60);

        // Force landscape for Android API < 14 (Ice Cream Sandwich)
        //   Earlier versions do not change screen size on orientation change
        if (mSettings.getBoolean(Value.LockLandscape, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        // Force no title for extra room
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Set the main view screen
        setContentView(R.layout.main);
        mMainView = findViewById(R.id.main_view);
        mSolitaireView = findViewById(R.id.solitaire);
        mSolitaireView.setActivity(this);
        mSolitaireView.SetTextView(findViewById(R.id.text));
        loadInterstitialAd(this);
        restoreLives();

        //StartSolitaire(savedInstanceState);
        //context menu
        //registerForContextMenu(mSolitaireView);

        // Set global variable for versionName
        try {
            VERSION_NAME = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            Log.e("SolitaireCG.java", e.getMessage());
        }

        findViewById(R.id.imageExpand).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            setMenu();
        });
        findViewById(R.id.imageExpand).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                MPlayer.playClicked(SolitaireCG.this);
                setMenu();
                return true;
            }
            return false;
        });
        startLiveAction();
    }

    private Context c() {
        return getApplicationContext();
    }

    private void restoreLives() {
        Calendar cal = Calendar.getInstance();
        today = "today" + cal.get(Calendar.DAY_OF_MONTH) + cal.get(Calendar.MONTH) + cal.get(Calendar.YEAR);
        System.out.println("TODAY is : " + today);
        if (prefDay.getBoolean(today, true)) {
            GetSettings().edit().putInt(Value.HoursLeaving, 0).apply();
            if (getLives() < 5) {
                Toast.makeText(c(), getString(R.string.lives_recharged), Toast.LENGTH_SHORT).show();
                saveLives(5);
            }
            prefDay.edit().putBoolean(today, false).apply();
        }
        setOfflineLive();
    }

    private void startLiveAction() {
        mMainView.postDelayed(timeAction, 1000);
    }

    private final Runnable timeAction = () -> {
        seconds -= 1;
        if (seconds == -1) {
            seconds = 59;
            minutes -=1;
            if (minutes == -1) {
                minutes = 29;
                addLive();
            }
            saveTimer();
        }
        if (textCountTimer !=null)
            if (getLives() < 5) {
                textCountTimer.setVisibility(View.VISIBLE);
                String minuteStr, secondStr;
                if (minutes < 10)
                    minuteStr = "0" + minutes;
                else
                    minuteStr = "" + minutes;
                if (seconds < 10)
                    secondStr = "0" + seconds;
                else
                    secondStr = "" + seconds;
                liveTime = minuteStr + ":" + secondStr;
                textCountTimer.setText(liveTime);
            } else {
                textCountTimer.setVisibility(View.GONE);
            }
        startLiveAction();
    };

    private void setOfflineLive() {
        if (!prefDay.getBoolean(today, true)) {
            int hoursLeaving = GetSettings().getInt(Value.HoursLeaving, 0);
            int hoursNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            System.out.println("HOUR NOW = " + hoursNow + ", HOURS LEAVING = " + hoursLeaving);
            if (hoursLeaving < 1)
                return;
            int totalHoursLeaving = hoursNow - hoursLeaving;
            if (totalHoursLeaving < 1)
                return;
            if (totalHoursLeaving > 5)
                totalHoursLeaving = 5;
            if (getLives() < 5)
                saveLives(getLives() + totalHoursLeaving);
            if (getLives() > 5)
                saveLives(5);
        }
    }

    private void addLive() {
        int live = getLives();
        if (live < 5) {
            live += 1;
            setLiveNotification(this, "+1", R.drawable.ic_lives);
        } else
            live = 5;
        saveLives(live);
        if (layoutLives !=null)
            setLayoutLives(layoutLives);
    }

    private void saveTimer() {
        GetSettings().edit().putInt(Value.Minutes, minutes).apply();
        GetSettings().edit().putInt(Value.Seconds, seconds).apply();
    }

    private void saveLives(int lives) {
        GetSettings().edit().putInt(Value.Lives, lives).apply();
    }

    private int getLives() {
        return GetSettings().getInt(Value.Lives, 5);
    }

    public static void loadInterstitialAd(Context context) {
        String testAd = "ca-app-pub-3940256099942544/8691691433"; //todo test ad
        InterstitialAd.load(context, testAd, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                super.onAdLoaded(interstitialAd);
                interstitialAd = ad;
            }
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                interstitialAd = null;
            }
        });
    }

    private void loadRewardAd() {
        String testAd = "ca-app-pub-3940256099942544/5224354917"; //todo test ad
        RewardedAd.load(this, testAd, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd reward) {
                super.onAdLoaded(rewardedAd);
                rewardedAd = reward;
                if (noLive !=null) {
                    noLive.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_add, 0);
                    noLive.setClickable(true);
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                super.onAdFailedToLoad(loadAdError);
                rewardedAd = null;
            }
        });
    }

    // Entry point for starting the game.
    //public void StartSolitaire(Bundle savedInstanceState) {
    @Override
    public void onStart() {
        super.onStart();
        mSolitaireView.onStart();

        if (mSettings.getBoolean(Value.SolitaireSaveValid, false)) {
            SharedPreferences.Editor editor = GetSettings().edit();
            editor.putBoolean(Value.SolitaireSaveValid, false);
            editor.apply();
            // If save is corrupt, just start a new game.
            if (mSolitaireView.LoadSave()) {
                SplashScreen();
                return;
            }
        }

        mSolitaireView.InitGame(mSettings.getInt(Value.LastType, Rules.KLONDIKE));
        SplashScreen();
    }

    // Force show splash screen if this is the first time played.
    private void SplashScreen() {
        if (!mSettings.getBoolean(Value.PlayedBefore, false)) {
            mSolitaireView.DisplaySplash();
        }
    }

    private int getCoinTalent() {
        return GetSettings().getInt(Value.CoinTalent, 0);
    }

    private void setMenu() {
        if (rewardedAd == null)
            loadRewardAd();
        dialogMenu = new BottomSheetDialog(this);
        View viewMenu = getLayoutInflater().inflate(R.layout.options_bottom_sheet, findViewById(R.id.menuRoot));
        viewMenu.findViewById(R.id.textSelectGame).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            setSelectGameMenu(v);
        });
        layoutLives = viewMenu.findViewById(R.id.layoutLives);
        textCountTimer = viewMenu.findViewById(R.id.textCountTimer);
        TextView textCoinTalent = viewMenu.findViewById(R.id.textCoinTalent);
        textCoinTalent.setText(String.valueOf(getCoinTalent()));
        textCountTimer.setText(liveTime);
        int lives = getLives();
        if (lives > 0) {
            setLayoutLives(layoutLives);
        } else {
            noLive = new TextView(this);
            if (rewardedAd == null) {
                noLive.setClickable(false);
                noLive.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            } else {
                noLive.setClickable(true);
                noLive.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_add, 0);
            }
            noLive.setText(getString(R.string.out_of_lives));
            noLive.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            noLive.setTextColor(Color.parseColor("#C72815"));
            noLive.setCompoundDrawablePadding(5);
            layoutLives.addView(noLive);
            noLive.setOnClickListener(v -> {
                MPlayer.playClicked(SolitaireCG.this);
                AlertDialog.Builder builder = new AlertDialog.Builder(SolitaireCG.this);
                View view = getLayoutInflater().inflate(R.layout.dialog_add_lives, null);
                builder.setView(view);
                AlertDialog dialogAddLive = builder.create();
                if (dialogAddLive.getWindow() !=null)
                    dialogAddLive.getWindow().setBackgroundDrawable(new ColorDrawable(0));
                view.findViewById(R.id.imageClose).setOnClickListener(v1 -> {
                    MPlayer.playClicked(SolitaireCG.this);
                    dialogAddLive.dismiss();
                });
                view.findViewById(R.id.buttonWatch).setOnClickListener(v1 -> {
                    MPlayer.playClicked(SolitaireCG.this);
                    if (rewardedAd !=null) {
                        dialogAddLive.dismiss();
                        rewardedAd.show(SolitaireCG.this, rewardItem -> setLivesRecharged());
                    }
                });
                view.findViewById(R.id.buttonUseCoin).setOnClickListener(v1 -> {
                    MPlayer.playClicked(SolitaireCG.this);
                    if (getCoinTalent() < 2) {
                        Toast.makeText(c(), getString(R.string.coin_not_enough), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialogAddLive.dismiss();
                    GetSettings().edit().putInt(Value.CoinTalent, getCoinTalent() - 2).apply();
                    textCoinTalent.setText(String.valueOf(getCoinTalent()));
                    setLivesRecharged();
                });
                dialogAddLive.show();
            });
        }
        viewMenu.findViewById(R.id.textNewGame).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            if (getLives() < 1) {
                Toast.makeText(c(), getString(R.string.not_enough_live), Toast.LENGTH_SHORT).show();
                return;
            }
            GetSettings().edit().putInt(Value.Lives, getLives() - 1).apply();
            setLiveNotification(this, "-1", R.drawable.ic_lives);
            dialogMenu.dismiss();
            mSolitaireView.InitGame(mSettings.getInt(Value.LastType, Rules.KLONDIKE));
        });
        viewMenu.findViewById(R.id.textRestartGame).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
            mSolitaireView.RestartGame();
        });
        viewMenu.findViewById(R.id.textOptions).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
            DisplayOptions();
        });
        viewMenu.findViewById(R.id.textStats).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
            DisplayStats();
        });
        viewMenu.findViewById(R.id.textHelp).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
            DisplayHelp();
        });
        viewMenu.findViewById(R.id.textInfo).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogInfo();
        });
        viewMenu.findViewById(R.id.textExit).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
            if (interstitialAd !=null) {
                interstitialAd.show(SolitaireCG.this);
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        super.onAdDismissedFullScreenContent();
                        finish();
                    }
                });
            } else finish();
        });
        viewMenu.findViewById(R.id.imageBack).setOnClickListener(v -> {
            MPlayer.playClicked(SolitaireCG.this);
            dialogMenu.dismiss();
        });
        dialogMenu.setContentView(viewMenu);
        FrameLayout layout = dialogMenu.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (layout !=null) {
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(layout);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setPeekHeight(getResources().getDisplayMetrics().heightPixels);
        }
        dialogMenu.show();
    }

    private void dialogInfo() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View viewInfo = getLayoutInflater().inflate(R.layout.dialog_info, null);
        ((TextView) viewInfo.findViewById(R.id.textDeveloped)).setText(HtmlCompat.fromHtml(getString(R.string.copyright_text),
                HtmlCompat.FROM_HTML_MODE_LEGACY));
        builder.setView(viewInfo);
        AlertDialog dialogInfo = builder.create();
        if (dialogInfo.getWindow() !=null)
            dialogInfo.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        viewInfo.findViewById(R.id.textPrivacy).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://www.gwnbs.com/p/solitairecg-privacy-policy.html"))));
        viewInfo.findViewById(R.id.textMore).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://play.google.com/store/apps/developer?id=Daltray+Nababan"))));
        viewInfo.findViewById(R.id.textOpenSource).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://sourceforge.net/p/solitairecg/code/ci/master/tree/"))));
        viewInfo.findViewById(R.id.imageClose).setOnClickListener(v -> dialogInfo.dismiss());
        dialogInfo.show();
    }

    private void setLivesRecharged() {
        saveLives(5);
        setLayoutLives(layoutLives);
        Toast.makeText(c(), getString(R.string.lives_recharged), Toast.LENGTH_SHORT).show();
    }

    private void setLayoutLives(LinearLayout layoutLives) {
        layoutLives.removeAllViews();
        int lives = getLives();
        ImageView[] imageLives = new ImageView[lives];
        for (int i = 0; i < lives; i++) {
            imageLives[i] = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(2, 0, 0, 0);
            imageLives[i].setLayoutParams(params);
            imageLives[i].setImageResource(R.drawable.ic_lives);
            layoutLives.addView(imageLives[i]);
        }
    }

    public static void setCoinNotification(Activity scg) {
        setLiveNotification(scg, "+1", R.drawable.ic_chip);
    }

    public static void setLiveNotification(Activity scg, String notif, int drawable) {
        TextView textLiveNotification = scg.findViewById(R.id.textLiveNotification);
        textLiveNotification.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
        textLiveNotification.setText(notif);
        textLiveNotification.setTranslationX(1200);
        textLiveNotification.setVisibility(View.VISIBLE);
        textLiveNotification.animate().translationX(0).setDuration(1000).withEndAction(() ->
                new CountDownTimer(1500, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) { }
                    @Override
                    public void onFinish() {
                        textLiveNotification.animate().translationX(-1200).alpha(0.3f).setDuration(1000).withEndAction(() -> {
                            textLiveNotification.setVisibility(View.GONE);
                            textLiveNotification.setTranslationX(0);
                            textLiveNotification.setAlpha(1f);
                        }).start();
                    }
                }.start()).start();
    }

    private void setSelectGameMenu(View v) {
        PopupMenu popupMenu = new PopupMenu(this, v);
        popupMenu.inflate(R.menu.game_menu);
        popupMenu.setOnMenuItemClickListener(item -> {
            MPlayer.playClicked(SolitaireCG.this);
            int lives = getLives();
            if (lives == 0) {
                Toast.makeText(c(), getString(R.string.not_enough_live), Toast.LENGTH_SHORT).show();
                return true;
            }
            int id = item.getItemId();
            ClearRestoreState();
            SharedPreferences.Editor editor = GetSettings().edit();
            if (id == R.id.fortyThieves) {
                mSolitaireView.InitGame(Rules.FORTYTHIEVES);
            } else if (id == R.id.freeCellAltColor) {
                editor.putBoolean(Value.FreecellBuildBySuit, false); //BuildByAlternateColor
                editor.apply();
                mSolitaireView.InitGame(Rules.FREECELL);
            } else if (id == R.id.freeCellBakers) {
                editor.putBoolean(Value.FreecellBuildBySuit, true);
                editor.apply();
                mSolitaireView.InitGame(Rules.FREECELL);
            } else if (id == R.id.golfNoBuild) {
                editor.putBoolean(Value.GolfWrapCards, false); //No build on King
                editor.apply();
                mSolitaireView.InitGame(Rules.GOLF);
            } else if (id == R.id.golfWrapCards) {
                editor.putBoolean(Value.GolfWrapCards, true); //WrapCards (A,Q on K, etc.)
                editor.apply();
                mSolitaireView.InitGame(Rules.GOLF);
            } else if (id == R.id.klondikeDeal1) {
                editor.putBoolean(Value.KlondikeDealThree, false);
                editor.putBoolean(Value.KlondikeStyleNormal, true);
                editor.apply();
                mSolitaireView.InitGame(Rules.KLONDIKE);
            } else if (id == R.id.klondikeDeal3) {
                editor.putBoolean(Value.KlondikeDealThree, true);
                editor.putBoolean(Value.KlondikeStyleNormal, true);
                editor.apply();
                mSolitaireView.InitGame(Rules.KLONDIKE);
            } else if (id == R.id.spider4Suits) {
                editor.putInt(Value.SpiderSuits, 4);
                editor.apply();
                mSolitaireView.InitGame(Rules.SPIDER);
            } else if (id == R.id.spiderTarantula) {
                editor.putInt(Value.SpiderSuits, 2);
                editor.apply();
                mSolitaireView.InitGame(Rules.SPIDER);
            } else if (id == R.id.spiderBlackWidow) {
                editor.putInt(Value.SpiderSuits, 1);
                editor.apply();
                mSolitaireView.InitGame(Rules.SPIDER);
            } else if (id == R.id.triPeaksNoBuild) {
                editor.putBoolean(Value.GolfWrapCards, false); //No build on King
                editor.apply();
                mSolitaireView.InitGame(Rules.TRIPEAKS);
            } else if (id == R.id.triPeaksWrapCards) {
                editor.putBoolean(Value.GolfWrapCards, true); //WrapCards (A,Q on K, etc.)
                editor.apply();
                mSolitaireView.InitGame(Rules.TRIPEAKS);
            } else if (id == R.id.vegasDeal1) {
                editor.putBoolean(Value.KlondikeDealThree, false);
                editor.putBoolean(Value.KlondikeStyleNormal, false);
                editor.apply();
                mSolitaireView.InitGame(Rules.KLONDIKE);
            } else if (id == R.id.vegasDeal3) {
                editor.putBoolean(Value.KlondikeDealThree, true);
                editor.putBoolean(Value.KlondikeStyleNormal, false);
                editor.commit();
                mSolitaireView.InitGame(Rules.KLONDIKE);
            }
            lives -= 1;
            saveLives(lives);
            dialogMenu.dismiss();
            setLiveNotification(this, "-1", R.drawable.ic_lives);
            return false;
        });
        popupMenu.show();
    }

    // Alternate Menu
    // Invoked with long press and needed on some devices where Android
    // options menu is not accessible or available.

    @Override
    protected void onPause() {
        super.onPause();
        mSolitaireView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mSolitaireView.SaveGame();
    }

    // Capture state prior to configuration/orientation change
    @Override
    public Object onRetainNonConfigurationInstance() {
        final ConfigWrapper config = new ConfigWrapper();
        config.screen = GetRestoreState();
        return config;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        // Force landscape for Android API < 14 (Ice Cream Sandwich)
        //   Earlier versions do not change screen size on orientation change
        if (mSettings.getBoolean(Value.LockLandscape, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            // Needed to clear orientation when lock landscape option not set
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        mSolitaireView.onResume();

        // Restore previous state after configuration/orientation change
        if (GetRestoreState().equals("STATS")) {
            DisplayStats();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void DisplayOptions() {
        mSolitaireView.SetTimePassing(false);
        Intent settingsActivity = new Intent(this, Preferences.class);
        startActivity(settingsActivity);
    }

    public void DisplayHelp() {
        mSolitaireView.SetTimePassing(false);
        Intent helpActivity = new Intent(this, Help.class);
        startActivity(helpActivity);
    }

    public void DisplayStats() {
        SetRestoreState("STATS");
        mSolitaireView.SetTimePassing(false);
        new Stats(this, mSolitaireView);
    }

    public void CancelOptions() {
        ClearRestoreState();
        setContentView(mMainView);
        mSolitaireView.requestFocus();
        mSolitaireView.SetTimePassing(true);
    }

    // This is called for option changes that require a refresh, but not a new game
    /*public void RefreshOptions() {
        ClearRestoreState();
        setContentView(mMainView);
        mSolitaireView.RefreshOptions();
    } */

    @Override
    public void onBackPressed() {
        MPlayer.playClicked(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainView.removeCallbacks(timeAction);
        saveTimer();
        GetSettings().edit().putInt(Value.HoursLeaving, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)).apply();
    }
}

