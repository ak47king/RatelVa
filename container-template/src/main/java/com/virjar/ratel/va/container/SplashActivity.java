package com.virjar.ratel.va.container;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.TextView;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.remote.InstallResult;

import io.virtualapp.R;


public class SplashActivity extends AppCompatActivity {

    private volatile boolean delegateAppReady = false;
    private TextView hintTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        hintTextView = (TextView) findViewById(R.id.hintMessge);
        new Thread() {
            @Override
            public void run() {
                if (!VirtualCore.get().isEngineLaunched()) {
                    updateHintMessage(R.string.launchEngine);
                    VirtualCore.get().waitForEngine();
                }

                if (!VirtualCore.get().isPackageLaunchable(VApp.getApp().getDelegateAppPackage())) {
                    updateHintMessage(R.string.installEmbedApk);
                    InstallResult installResult = VirtualCore.get().installPackage(VApp.getApp().getDelegateApkFile().getAbsolutePath(), 0);
                    if (installResult == null || !installResult.isSuccess) {
                        String errorMessage = "unknown";
                        if (installResult != null) {
                            errorMessage = installResult.error;
                        }
                        throw new IllegalStateException(errorMessage);
                    }
                }

                delegateAppReady = true;
                updateHintMessage(R.string.preparing);
                SplashActivity.this.startDelegateApk();
            }
        }.start();

    }

    private void updateHintMessage(int stringResourceId) {
        runOnUiThread(() -> hintTextView.setText(stringResourceId));
    }

    private void startDelegateApk() {

        int userId = 0;

        Intent intent = VirtualCore.get().getLaunchIntent(VApp.getApp().getDelegateAppPackage(), userId);
        if (intent == null) {
            return;
        }

        VirtualCore.get().setUiCallback(intent, new VirtualCore.UiCallback() {
            @Override
            public void onAppOpened(String packageName, int userId) {
                finish();
            }
        });
        try {
            VirtualCore.get().preOpt(VApp.getApp().getDelegateAppPackage());
        } catch (Exception e) {
            e.printStackTrace();
        }

        ActivityInfo info = VirtualCore.get().resolveActivityInfo(intent, userId);
        info.launchMode = ActivityInfo.LAUNCH_SINGLE_INSTANCE;


        VActivityManager.get().startActivity(intent, info, null, null, null, 0, 0);

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (delegateAppReady) {
            SplashActivity.this.startDelegateApk();
        }
    }
}
