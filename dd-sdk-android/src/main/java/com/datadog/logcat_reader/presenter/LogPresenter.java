package com.datadog.logcat_reader.presenter;

import android.util.Log;

import androidx.annotation.CheckResult;

import com.datadog.logcat_reader.LogReaderConfig;
import com.datadog.logcat_reader.model.LogcatReader;
import com.datadog.logcat_reader.model.Trace;
import com.datadog.logcat_reader.model.TraceLevel;

import java.util.LinkedList;
import java.util.List;

/**
 * Presenter created to decouple Lynx library view implementations from Lynx model. This presenter
 * responsibility is related to all the presentation logic to Lynx UI implementations. Lynx UI
 * implementations have to implement LynxPresenter.View interface.
 */
public class LogPresenter implements LogcatReader.Listener {

  private String strLogcatTrace;

  private static final int MIN_VISIBLE_POSITION_TO_ENABLE_AUTO_SCROLL = 3;

  private final LogcatReader logcatReader;
  private final TraceBuffer traceBuffer;
  private boolean isInitialized;

  public LogPresenter(LogcatReader logcatReader, int maxNumberOfTracesToShow) {
    validateNumberOfTracesConfiguration(maxNumberOfTracesToShow);
    this.logcatReader = logcatReader;
    this.traceBuffer = new TraceBuffer(maxNumberOfTracesToShow);
  }

  /**
   * Updates and applies a new lynx configuration based on the LynxConfig object passed as
   * parameter.
   *
   * @param logReaderConfig the lynx configuration
   */
  public void setLogcatReaderConfig(LogReaderConfig logReaderConfig) {
    validateLynxConfig(logReaderConfig);
    updateBufferConfig(logReaderConfig);
    updateLynxConfig(logReaderConfig);
  }

  /**
   * Initializes presenter lifecycle if it wasn't initialized before.
   */
  public void resume() {
    if (!isInitialized) {
      isInitialized = true;
      logcatReader.registerListener(this);
      logcatReader.startReading();
      Log.d("LynxPresenter", "strLogcatTrace: " + strLogcatTrace);
    }
  }

  /**
   * Stops presenter lifecycle if it was previously initialized.
   */
  public void pause() {
    if (isInitialized) {
      isInitialized = false;
      logcatReader.stopReading();
      logcatReader.unregisterListener(this);
    }
  }

  /**
   * Given a list of Trace objects to show, updates the buffer of traces and refresh the view.
   * @return
   */
  @Override public String onNewTraces(List<Trace> traces) {
    int tracesRemoved = updateTraceBuffer(traces);
    List<Trace> tracesToNotify = getCurrentTraces();
    String plainTraces = generatePlainTracesToShare(tracesToNotify.get(tracesToNotify.size() - 1));
    Log.e("LynxPresenter", "onNewTraces: " + plainTraces);
    // Removed View
    return plainTraces;
  }

  /**
   * Updates the filter used to know which Trace objects we have to show in the UI.
   *
   * @param filter the filter to use
   */
  public void updateFilter(String filter) {
    if (isInitialized) {
      LogReaderConfig logReaderConfig = logcatReader.getConfig();
      logReaderConfig.setFilter(filter);
      logcatReader.setConfig(logReaderConfig);
      // Removed View
      restartLynx();
    }
  }

  public void updateFilterTraceLevel(TraceLevel level) {
    if (isInitialized) {
      // Removed View
      LogReaderConfig logReaderConfig = logcatReader.getConfig();
      logReaderConfig.setFilterTraceLevel(level);
      logcatReader.setConfig(logReaderConfig);
      restartLynx();
    }
  }

  /**
   * Generates a plain representation of all the Trace objects this presenter has stored and share
   * them to other applications.
   */
  public String onShareButtonClicked() {
    List<Trace> tracesToShare = new LinkedList<Trace>(traceBuffer.getTraces());
    String plainTraces = generatePlainTracesToShare(tracesToShare.get(tracesToShare.size() - 1));
    // Removed View
    return plainTraces;
  }

  /**
   * Returns a list of the current traces stored in this presenter.
   *
   * @return a list of the current traces
   */
  public List<Trace> getCurrentTraces() {
    return traceBuffer.getTraces();
  }

  private void restartLynx() {
    logcatReader.restart();
  }

  private void updateBufferConfig(LogReaderConfig logReaderConfig) {
    traceBuffer.setBufferSize(logReaderConfig.getMaxNumberOfTracesToShow());
    refreshTraces();
  }

  private void refreshTraces() {
    onNewTraces(traceBuffer.getTraces());
  }

  private void updateLynxConfig(LogReaderConfig logReaderConfig) {
    logcatReader.setConfig(logReaderConfig);
  }

  private int updateTraceBuffer(List<Trace> traces) {
    return traceBuffer.add(traces);
  }

  private void validateNumberOfTracesConfiguration(long maxNumberOfTracesToShow) {
    if (maxNumberOfTracesToShow <= 0) {
      throw new IllegalArgumentException(
          "You can't pass a zero or negative number of traces to show.");
    }
  }

  private void validateLynxConfig(LogReaderConfig logReaderConfig) {
    if (logReaderConfig == null) {
      throw new IllegalArgumentException(
          "You can't use a null instance of LynxConfig as configuration.");
    }
  }

  private boolean shouldDisableAutoScroll(int lastVisiblePosition) {
    int positionOffset = traceBuffer.getCurrentNumberOfTraces() - lastVisiblePosition;
    return positionOffset >= MIN_VISIBLE_POSITION_TO_ENABLE_AUTO_SCROLL;
  }

  private String generatePlainTracesToShare(Trace trace) {
    StringBuilder sb = new StringBuilder();
//    for (Trace trace : tracesToShare) {
      String traceLevel = trace.getLevel().getValue();
      String traceMessage = trace.getMessage();
      sb.append(traceLevel);
      sb.append("/ ");
      sb.append(traceMessage);
      sb.append("\n");
//    }
    return sb.toString();
  }

  public interface View {

    void showTraces(List<Trace> traces, int removedTraces);

    void clear();

    @CheckResult boolean shareTraces(String plainTraces);

    void notifyShareTracesFailed();

    void disableAutoScroll();

    void enableAutoScroll();
  }
}
