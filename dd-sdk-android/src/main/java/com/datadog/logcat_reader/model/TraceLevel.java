package com.datadog.logcat_reader.model;

/**
 * Logcat trace levels used to indicate the trace importance.
 */
public enum TraceLevel {
  VERBOSE("V"), DEBUG("D"), INFO("I"), WARNING("W"), ERROR("E"), ASSERT("A"), WTF("F");

  private final String value;

  TraceLevel(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static TraceLevel getTraceLevel(char traceString) {
    TraceLevel traceLevel;
    switch (traceString) {
      case 'V':
        traceLevel = TraceLevel.VERBOSE;
        break;
      case 'A':
        traceLevel = TraceLevel.ASSERT;
        break;
      case 'I':
        traceLevel = TraceLevel.INFO;
        break;
      case 'W':
        traceLevel = TraceLevel.WARNING;
        break;
      case 'E':
        traceLevel = TraceLevel.ERROR;
        break;
      case 'F':
        traceLevel = TraceLevel.WTF;
        break;
      default:
        traceLevel = TraceLevel.DEBUG;
    }
    return traceLevel;
  }
}
