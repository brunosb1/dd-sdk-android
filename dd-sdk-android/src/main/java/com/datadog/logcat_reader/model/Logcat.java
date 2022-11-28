package com.datadog.logcat_reader.model;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Logcat abstraction created to be able to read from the device log output. This implementation is
 * based on a BufferReader connected to the process InputStream you can obtain executing a command
 * using Android Runtime object.
 *
 * This class will notify listeners configured previously about new traces sent to the device and
 * will be reading and notifying traces until stopReading() method be invoked.
 *
 * To be able to read from a process InputStream without block the thread where we were, this class
 * extends from Thread and all the code inside the run() method will be executed in a background
 * thread.
 */
public class Logcat extends Thread implements Cloneable {
  private static final String LOGTAG = "Logcat";

  private Process process;
  private BufferedReader bufferReader;
  private Listener listener;
  private boolean continueReading = true;

  /**
   * Configures a listener to be notified with new traces read from the application logcat.
   *
   * @param listener the Logcat listener
   */
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  /**
   * Obtains the current Logcat listener.
   *
   * @return the current Logcat listener
   */
  public Listener getListener() {
    return listener;
  }

  /**
   * Starts reading traces from the application logcat and notifying listeners if needed.
   */
  @Override public void run() {
    super.run();
    try {
      process = Runtime.getRuntime().exec("logcat -v time");
    } catch (IOException e) {
      Log.e(LOGTAG, "IOException executing logcat command.", e);
    }
    readLogcat();
  }

  /**
   * Stops reading from the application logcat and notifying listeners.
   */
  public void stopReading() {
    continueReading = false;
  }

  private void readLogcat() {
    BufferedReader bufferedReader = getBufferReader();
    try {
      String trace = bufferedReader.readLine();
      while (trace != null && continueReading) {
        notifyListener(trace);
        trace = bufferedReader.readLine();
      }
    } catch (IOException e) {
      Log.e(LOGTAG, "IOException reading logcat trace.", e);
    }
  }

  private void notifyListener(String trace) {
    if (listener != null) {
      listener.onTraceRead(trace);
    }
  }

  private BufferedReader getBufferReader() {
    if (bufferReader == null) {
      bufferReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }
    return bufferReader;
  }

  @Override public Object clone() {
    return new Logcat();
  }

  interface Listener {

    void onTraceRead(String logcatTrace);
  }
}
