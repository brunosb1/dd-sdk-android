package com.datadog.logcat_reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity created to show a LynxView with "match_parent" configuration for LynxView
 * "layout_height" and "layout_width" attributes. To configure LynxView and all the information to
 * show use Activity extras and a LynxConfig object. Use getIntent() method to obtain a valid
 * intent
 * to start this Activity.
 */
public class LogcatActivity extends Activity {

  private static final String LYNX_CONFIG_EXTRA = "extra_lynx_config";

  /**
   * Generates an Intent to start LynxActivity with a default LynxConfig object as configuration.
   *
   * @param context the application context
   * @return a new {@code Intent} to start {@link LogcatActivity}
   */
  public static Intent getIntent(Context context) {
    return getIntent(context, new LogReaderConfig());
  }

  /**
   * Generates an Intent to start LynxActivity with a LynxConfig configuration passed as parameter.
   *
   * @param context the application context
   * @param logReaderConfig the lynx configuration
   * @return a new {@code Intent} to start {@link LogcatActivity}
   */
  public static Intent getIntent(Context context, LogReaderConfig logReaderConfig) {
    if (logReaderConfig == null) {
      logReaderConfig = new LogReaderConfig();
    }
    Intent intent = new Intent(context, LogcatActivity.class);
    intent.putExtra(LYNX_CONFIG_EXTRA, logReaderConfig);
    return intent;
  }

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogReaderConfig logReaderConfig = getLynxConfig();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
  }

  private LogReaderConfig getLynxConfig() {
    Bundle extras = getIntent().getExtras();
    LogReaderConfig logReaderConfig = new LogReaderConfig();
    if (extras != null && extras.containsKey(LYNX_CONFIG_EXTRA)) {
      logReaderConfig = (LogReaderConfig) extras.getSerializable(LYNX_CONFIG_EXTRA);
    }
    return logReaderConfig;
  }
}
