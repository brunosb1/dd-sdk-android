package com.datadog.logcat_reader;


import java.io.Serializable;

import com.datadog.logcat_reader.model.TraceLevel;

/**
 * Lynx configuration parameters used to open main activity. All the configuration library is
 * provided by library clients using this class. With LynxConfig you can privde different values
 * for:
 *
 * - Max number of traces to show in LynxView.
 * - Filter used to get a list of traces to show.
 * - Text size in DP used to render a trace.
 * - Sampling rate used to read from the Logcat output.
 */
public class LogReaderConfig implements Serializable, Cloneable {
  private static final long serialVersionUID = 293939299388293L;

  private static final float DEFAULT_TEXT_SIZE_IN_PX = 36;

  private int maxNumberOfTracesToShow = 2500;
  private String filter;
  private TraceLevel filterTraceLevel;
  private Float textSizeInPx;
  private int samplingRate = 150;

  public LogReaderConfig() {
    filter = "";
    filterTraceLevel = TraceLevel.VERBOSE;
  }

  public LogReaderConfig setMaxNumberOfTracesToShow(int maxNumberOfTracesToShow) {
    if (maxNumberOfTracesToShow <= 0) {
      throw new IllegalArgumentException(
          "You can't use a max number of traces equals or lower than zero.");
    }

    this.maxNumberOfTracesToShow = maxNumberOfTracesToShow;
    return this;
  }

  public LogReaderConfig setFilter(String filter) {
    if (filter == null) {
      throw new IllegalArgumentException("filter can't be null");
    }
    this.filter = filter;
    return this;
  }

  public LogReaderConfig setFilterTraceLevel(TraceLevel filterTraceLevel) {
    if (filterTraceLevel == null) {
      throw new IllegalArgumentException("filterTraceLevel can't be null");
    }
    this.filterTraceLevel = filterTraceLevel;
    return this;
  }

  public LogReaderConfig setTextSizeInPx(float textSizeInPx) {
    this.textSizeInPx = textSizeInPx;
    return this;
  }

  public LogReaderConfig setSamplingRate(int samplingRate) {
    this.samplingRate = samplingRate;
    return this;
  }

  public int getMaxNumberOfTracesToShow() {
    return maxNumberOfTracesToShow;
  }

  public String getFilter() {
    return filter;
  }

  public TraceLevel getFilterTraceLevel() {
    return filterTraceLevel;
  }

  public boolean hasFilter() {
    return !"".equals(filter) || !TraceLevel.VERBOSE.equals(filterTraceLevel);
  }

  public float getTextSizeInPx() {
    return textSizeInPx == null ? DEFAULT_TEXT_SIZE_IN_PX : textSizeInPx;
  }

  public boolean hasTextSizeInPx() {
    return textSizeInPx != null;
  }

  public int getSamplingRate() {
    return samplingRate;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LogReaderConfig)) return false;

    LogReaderConfig that = (LogReaderConfig) o;

    if (maxNumberOfTracesToShow != that.maxNumberOfTracesToShow) return false;
    if (samplingRate != that.samplingRate) return false;
    if (filter != null ? !filter.equals(that.filter) : that.filter != null) return false;
    if (textSizeInPx != null ? !textSizeInPx.equals(that.textSizeInPx)
        : that.textSizeInPx != null) {
      return false;
    }
    if (filterTraceLevel != that.filterTraceLevel) return false;
    return true;
  }

  @Override public int hashCode() {
    int result = maxNumberOfTracesToShow;
    result = 31 * result + (filter != null ? filter.hashCode() : 0);
    result = 31 * result + (textSizeInPx != null ? textSizeInPx.hashCode() : 0);
    result = 31 * result + samplingRate;
    return result;
  }

  @Override public Object clone() {
    return new LogReaderConfig().setMaxNumberOfTracesToShow(getMaxNumberOfTracesToShow())
        .setFilter(filter)
        .setFilterTraceLevel(filterTraceLevel)
        .setSamplingRate(getSamplingRate());
  }

  @Override public String toString() {
    return "LynxConfig{"
        + "maxNumberOfTracesToShow="
        + maxNumberOfTracesToShow
        + ", filter='"
        + filter
        + '\''
        + ", textSizeInPx="
        + textSizeInPx
        + ", samplingRate="
        + samplingRate
        + '}';
  }
}
