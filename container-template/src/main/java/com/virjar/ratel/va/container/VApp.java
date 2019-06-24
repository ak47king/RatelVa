package com.virjar.ratel.va.container;

import android.content.Context;
import android.support.multidex.MultiDexApplication;

import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.stub.VASettings;
import com.lody.virtual.helper.utils.FileUtils;
import com.yc.nonsdk.NonSdkManager;

import net.dongliu.apk.parser.ApkFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Lody
 */
public class VApp extends MultiDexApplication {

    private static VApp gApp;

    public static VApp getApp() {
        return gApp;
    }

    private String delegateAppPackage;
    private File delegateApkFile;

    public String getDelegateAppPackage() {
        return delegateAppPackage;
    }

    public File getDelegateApkFile() {
        return delegateApkFile;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        VASettings.ENABLE_IO_REDIRECT = true;
        VASettings.ENABLE_INNER_SHORTCUT = false;
        NonSdkManager.getInstance().visibleAllApi();
        try {
            VirtualCore.get().startup(base);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        gApp = this;
        super.onCreate();
        VirtualCore virtualCore = VirtualCore.get();
        virtualCore.initialize(new VirtualCore.VirtualInitializer() {

            @Override
            public void onMainProcess() {
                delegateApkFile = new File(getFilesDir(), "inner_base.apk");
                try {
                    if (!delegateApkFile.exists()) {
                        InputStream inputStream = getAssets().open("base.apk");
                        FileUtils.writeToFile(inputStream, delegateApkFile);
                    }
                    ApkFile apkFile = new ApkFile(delegateApkFile);
                    delegateAppPackage = apkFile.getApkMeta().getPackageName();
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void onVirtualProcess() {
            }

            @Override
            public void onServerProcess() {
                //virtualCore.setAppRequestListener(new MyAppRequestListener(VApp.this));
                virtualCore.addVisibleOutsidePackage("com.tencent.mobileqq");
                virtualCore.addVisibleOutsidePackage("com.tencent.mobileqqi");
                virtualCore.addVisibleOutsidePackage("com.tencent.minihd.qq");
                virtualCore.addVisibleOutsidePackage("com.tencent.qqlite");
                virtualCore.addVisibleOutsidePackage("com.facebook.katana");
                virtualCore.addVisibleOutsidePackage("com.whatsapp");
                virtualCore.addVisibleOutsidePackage("com.tencent.mm");
                virtualCore.addVisibleOutsidePackage("com.immomo.momo");
            }
        });
    }


}
