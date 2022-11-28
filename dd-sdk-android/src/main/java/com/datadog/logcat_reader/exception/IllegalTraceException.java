package com.datadog.logcat_reader.exception;

/**
 * Custom exception created to notify when a Trace object is created with an invalid source. Review
 * Trace.fromString method to know the exact format of a Trace in the String representation needed
 * to avoid this exception.
 */
public class IllegalTraceException extends Exception {

  public IllegalTraceException(String detailMessage) {
    super(detailMessage);
  }
}
