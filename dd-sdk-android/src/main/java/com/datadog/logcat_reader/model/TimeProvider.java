package com.datadog.logcat_reader.model;

/**
 * Class created to add testability in terms of time usage. Using this wrapper of
 * System.currentTimeMillis instead of use the System call directly we improve our code
 * testability and provide mocked implementations of TimeProvider with pre-configured results.
 */
public class TimeProvider {

  public long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }
}
