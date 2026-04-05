package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BootstrapActivity extends Activity
{
    private ProgressBar progressBar;
    private TextView statusView;
    private TextView detailView;
    private Button retryButton;
    private volatile boolean installing;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bootstrap);

        progressBar = findViewById(R.id.install_progress);
        statusView = findViewById(R.id.install_status);
        detailView = findViewById(R.id.install_detail);
        retryButton = findViewById(R.id.install_retry);

        retryButton.setOnClickListener(view -> startInstall());
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if (!installing) {
            startInstall();
        }
    }

    private void startInstall()
    {
        if (installing) {
            return;
        }

        installing = true;
        retryButton.setVisibility(View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        statusView.setText(R.string.install_status_prepare);
        detailView.setText(R.string.install_detail_prepare);

        Thread installerThread = new Thread(() -> {
            try {
                PayloadInstaller.installOrUpdate(this, this::onInstallProgress);
                runOnUiThread(this::launchChapterSelect);
            } catch (Exception e) {
                runOnUiThread(() -> showFailure(e));
            } finally {
                installing = false;
            }
        }, "payload-installer");
        installerThread.start();
    }

    private void onInstallProgress(String phase, int percent)
    {
        runOnUiThread(() -> {
            progressBar.setProgress(percent);

            if ("verify".equals(phase)) {
                statusView.setText(R.string.install_status_verify);
                detailView.setText(getString(R.string.install_detail_percent, percent));
            } else if ("extract".equals(phase)) {
                statusView.setText(R.string.install_status_extract);
                detailView.setText(getString(R.string.install_detail_percent, percent));
            } else if ("finalize".equals(phase)) {
                statusView.setText(R.string.install_status_finalize);
                detailView.setText(R.string.install_detail_finalize);
            } else if ("retry".equals(phase)) {
                statusView.setText(R.string.install_status_retry);
                detailView.setText(R.string.install_detail_retry);
                progressBar.setProgress(0);
            }
        });
    }

    private void launchChapterSelect()
    {
        statusView.setText(R.string.install_status_done);
        detailView.setText(R.string.install_detail_chapter_select);
        progressBar.setProgress(100);

        Intent intent = new Intent(this, ChapterSelectActivity.class);
        startActivity(intent);
        finish();
    }

    private void showFailure(Exception error)
    {
        statusView.setText(R.string.install_status_failed);
        detailView.setText(error.getMessage());
        retryButton.setVisibility(View.VISIBLE);
    }
}
