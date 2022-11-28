package com.datadog.logcat_reader.model;

import android.os.Handler;
import android.os.Looper;

/**
 * MainThread implementation based on Android Handler and Looper classes. This class is used to
 * post Runnable objects over the UI.
 */
public class AndroidMainThread implements MainThread {

  private final Handler handler;

  public AndroidMainThread() {
    handler = new Handler(Looper.getMainLooper());
  }

  public void post(Runnable runnable) {
    handler.post(runnable);
  }
}
