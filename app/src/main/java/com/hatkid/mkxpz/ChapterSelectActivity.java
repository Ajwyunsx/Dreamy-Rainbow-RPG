package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ChapterSelectActivity extends Activity
{
    private TextView errorView;
    private Button chapter1Button;
    private Button chapter2Button;
    private Button chapter3Button;
    private Button chapter4Button;
    private Button chapterEndButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chapter_select);

        errorView = findViewById(R.id.chapter_error);
        chapter1Button = findViewById(R.id.chapter_button_1);
        chapter2Button = findViewById(R.id.chapter_button_2);
        chapter3Button = findViewById(R.id.chapter_button_3);
        chapter4Button = findViewById(R.id.chapter_button_4);
        chapterEndButton = findViewById(R.id.chapter_button_end);

        chapter1Button.setOnClickListener(view -> launchChapter("1"));
        chapter2Button.setOnClickListener(view -> launchChapter("2"));
        chapter3Button.setOnClickListener(view -> launchChapter("3"));
        chapter4Button.setOnClickListener(view -> launchChapter("4"));
        chapterEndButton.setOnClickListener(view -> launchChapter("END"));
    }

    private void launchChapter(String chapterId)
    {
        setButtonsEnabled(false);
        errorView.setVisibility(View.GONE);

        try {
            Intent intent = new Intent(this, MainActivity.class);
            ChapterLaunchConfig.applyToIntent(intent, chapterId);
            startActivity(intent);
            finish();
        } catch (Exception error) {
            errorView.setText(error.getMessage());
            errorView.setVisibility(View.VISIBLE);
            setButtonsEnabled(true);
        }
    }

    private void setButtonsEnabled(boolean enabled)
    {
        chapter1Button.setEnabled(enabled);
        chapter2Button.setEnabled(enabled);
        chapter3Button.setEnabled(enabled);
        chapter4Button.setEnabled(enabled);
        chapterEndButton.setEnabled(enabled);
    }
}
