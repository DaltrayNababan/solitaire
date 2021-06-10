/*
  Copyright 2015, 2016, 2017 Curtis Gedak

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.gwnbs.solitaire;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.Objects;

//import android.preference.PreferenceManager;

public class Help extends AppCompatActivity {

    // View extracted from help.xml
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Force landscape for Android API < 14 (Ice Cream Sandwich)
        //   Earlier versions do not change screen size on orientation change
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Value.LockLandscape, false)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        // Force no title for extra room
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.help);
        mWebView = findViewById(R.id.help_webview);
        // Always load help contents on configuration changes, for example
        // on rotation.
        // WebView.saveState/.restoreState no longer stores the display
        // data for the WebView.
        // https://developer.android.com/reference/android/webkit/WebView.html#saveState%28android.os.Bundle%29
        LoadHelpContents();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void LoadHelpContents() {
        // Load help contents
        // Alternatively:
        //   mWebView.loadUrl("file:///android_res/raw/help_contents.txt");
        String helpText;
        String locale = Locale.getDefault().getLanguage();
        if (locale.equals("in")) {
            helpText = "<html><body>" + "<a name=\"top\"></a>"
                    + "<h1>" + String.format(this.getString(R.string.help_window_title), SolitaireCG.VERSION_NAME) + "</h1>"
                    + Objects.requireNonNull(Utils.readRawTextFile(this, R.raw.bantuan_konten)).replace("\n", " ")
                    // Append README file
                    + "</body></html>";
        } else {
            helpText = "<html><body>" + "<a name=\"top\"></a>"
                    + "<h1>" + String.format(this.getString(R.string.help_window_title), SolitaireCG.VERSION_NAME) + "</h1>"
                    + Objects.requireNonNull(Utils.readRawTextFile(this, R.raw.help_contents)).replace("\n", " ")
                    // Append README file
                    + "</body></html>";
        }

        // Check for Android API <= 18 (Android 4.3 Jelly Bean) and load the
        // help text using loadData instead.  The reason is because
        // loadDataWithBaseURL() displays html as plain text on lower APIs.
        if (Build.VERSION.SDK_INT <= 18) {
            mWebView.loadData(helpText, "text/html; charset=utf-8", "utf-8");
        } else {
            mWebView.loadDataWithBaseURL("app:helpText", helpText, "text/html; charset=utf-8", "utf-8", "");
        }
    }

    // The following three methods attempt to maintain the user's
    // scroll position in the WebView when the screen is rotated.
    //
    // Note that the user's scroll position can be lost if the screen
    // is rapidly rotated to and fro.

    // Calculate percent of scroll progress in the actual web page content
    private int calculatePosition(WebView content) {
        float positionTopView = content.getTop();
        float contentHeight = content.getContentHeight();
        float currentScrollPosition = content.getScrollY();
        float percentWebview = (currentScrollPosition - positionTopView) / contentHeight;
        // Maintain percent with 2 decimal precision in an integer (x10000)
        return Math.round(percentWebview * 10000);
    }

    // Save webview scroll position
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntArray("Scroll_Position", new int[]{0, calculatePosition(mWebView)}
        );
    }

    // Restore webview scroll position
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        final WebView finalView = mWebView;
        final int[] position = savedInstanceState.getIntArray("Scroll_Position");
        if (position != null)
            mWebView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    float webviewsize = mWebView.getContentHeight() - mWebView.getTop();
                    if (webviewsize == 0) {
                        // Repeat delay to the scrollTo until page is finished loading
                        finalView.postDelayed(this, 10);
                        return;
                    }
                    float positionInWV = webviewsize * position[1] / 10000;
                    int positionY = Math.round(mWebView.getTop() + positionInWV);
                    mWebView.scrollTo(position[0], positionY);
                }
                // Delay the scrollTo until page is finished loading
            }, 10);
    }
}
