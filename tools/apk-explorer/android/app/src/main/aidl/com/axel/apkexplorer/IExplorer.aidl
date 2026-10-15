package com.axel.apkexplorer;
import android.os.ParcelFileDescriptor;
interface IExplorer {
    void destroy() = 16777114;
    String start(String packageName, int maxScreens) = 0;
    String status() = 1;
    void stop() = 2;
    ParcelFileDescriptor exportZip() = 3;
    String replay(String plan) = 4;
}
