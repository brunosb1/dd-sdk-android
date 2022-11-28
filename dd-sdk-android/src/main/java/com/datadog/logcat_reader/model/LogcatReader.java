package com.datadog.logcat_reader.model;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.util.Log;

import com.datadog.logcat_reader.LogReaderConfig;
import com.datadog.logcat_reader.exception.IllegalTraceException;

/**
 * Main business logic class for this project. Lynx responsibility is related to listen Logcat
 * events and notify it to the Lynx listeners transforming all the information from a plain String
 * to a Trace with all the information needed.
 *
 * Given a LynxConfig object the sample rating used to notify Lynx clients about new traces can be
 * modified on demand. LynxConfig object will be used to filter traces if any filter has been
 * previously configured. Filtering will remove traces that contains given string or that match a
 * regular expression specified as filter.
 */
public class LogcatReader {

  private static final String LOGTAG = "LogcatReader";

  private Logcat logcat;
  private final MainThread mainThread;
  private final TimeProvider timeProvider;
  private final List<Trace> tracesToNotify;
  private final List<Listener> listeners;

  private LogReaderConfig logReaderConfig = new LogReaderConfig();
  private long lastNotificationTime;

  private String lowerCaseFilter = "";
  private Pattern regexpFilter;

  public LogcatReader(Logcat logcat, MainThread mainThread, TimeProvider timeProvider) {
    this.listeners = new LinkedList<>();
    this.tracesToNotify = new LinkedList<>();
    this.logcat = logcat;
    this.mainThread = mainThread;
    this.timeProvider = timeProvider;
    setFilters();
  }

  /**
   * Indicates a custom LynxConfig object.
   *
   * @param logReaderConfig a custom LynxConfig object
   */
  public synchronized void setConfig(LogReaderConfig logReaderConfig) {
    this.logReaderConfig = logReaderConfig;
    setFilters();
  }

  /**
   * Returns a copy of the current LynxConfig object.
   *
   * @return a copy of the current LynxConfig object
   */
  public LogReaderConfig getConfig() {
    return (LogReaderConfig) logReaderConfig.clone();
  }

  /**
   * Configures a Logcat.Listener and initialize Logcat dependency to read traces from the OS log.
   */
  public void startReading() {
    logcat.setListener(new Logcat.Listener() {
      @Override public void onTraceRead(String logcatTrace) {
        try {
          addTraceToTheBuffer(logcatTrace);
        } catch (IllegalTraceException e) {
          return;
        }
        notifyNewTraces();
      }
    });
    boolean logcatWasNotStarted = Thread.State.NEW.equals(logcat.getState());
    if (logcatWasNotStarted) {
      logcat.start();
    }
  }

  /**
   * Stops Logcat dependency to stop receiving logcat traces.
   */
  public void stopReading() {
    logcat.stopReading();
    logcat.interrupt();
  }

  /**
   * Stops the configured Logcat dependency and creates a clone to restart using Logcat and
   * LogcatListener configured previously.
   */
  public synchronized void restart() {
    Logcat.Listener previousListener = logcat.getListener();
    logcat.stopReading();
    logcat.interrupt();
    logcat = (Logcat) logcat.clone();
    logcat.setListener(previousListener);
    lastNotificationTime = 0;
    tracesToNotify.clear();
    logcat.start();
  }

  /**
   * Adds a Listener to the listeners collection to be notified with new Trace objects.
   *
   * @param lynxPresenter a lynx listener
   */
  public synchronized void registerListener(Listener lynxPresenter) {
    listeners.add(lynxPresenter);
  }

  /**
   * Removes a Listener to the listeners collection.
   *
   * @param lynxPresenter a lynx listener
   */
  public synchronized void unregisterListener(Listener lynxPresenter) {
    listeners.remove(lynxPresenter);
  }

  private void setFilters() {
    lowerCaseFilter = logReaderConfig.getFilter().toLowerCase();
    try {
      regexpFilter = Pattern.compile(lowerCaseFilter);
    } catch (PatternSyntaxException exception) {
      regexpFilter = null;
      Log.d(LOGTAG, "Invalid regexp filter!");
    }
  }

  private synchronized void addTraceToTheBuffer(String logcatTrace) throws IllegalTraceException {
    if (shouldAddTrace(logcatTrace)) {
      Trace trace = Trace.fromString(logcatTrace);
      tracesToNotify.add(trace);
    }
  }

  private boolean shouldAddTrace(String logcatTrace) {
    boolean hasFilterConfigured = logReaderConfig.hasFilter();
    boolean hasMinSize = logcatTrace.length() >= Trace.MIN_TRACE_SIZE;
    return hasMinSize && (!hasFilterConfigured || traceMatchesFilter(logcatTrace));
  }

  private synchronized boolean traceMatchesFilter(String logcatTrace) {
    return traceStringMatchesFilter(logcatTrace)
            && containsTraceLevel(logcatTrace, logReaderConfig.getFilterTraceLevel());
  }

  private boolean traceStringMatchesFilter(String logcatTrace) {
    String lowerCaseLogcatTrace = logcatTrace.toLowerCase();
    boolean matchesFilter = lowerCaseLogcatTrace.contains(lowerCaseFilter);
    if (!matchesFilter && regexpFilter != null) {
      matchesFilter = regexpFilter.matcher(lowerCaseLogcatTrace).find();
    }
    return matchesFilter;
  }

  private boolean containsTraceLevel(String logcatTrace, TraceLevel levelFilter) {
    return levelFilter.equals(TraceLevel.VERBOSE) || hasTraceLevelEqualOrHigher(logcatTrace,
        levelFilter);
  }

  private boolean hasTraceLevelEqualOrHigher(String logcatTrace, TraceLevel levelFilter) {
    TraceLevel level = TraceLevel.getTraceLevel(logcatTrace.charAt(Trace.TRACE_LEVEL_INDEX));
    return level.ordinal() >= levelFilter.ordinal();
  }

  private synchronized void notifyNewTraces() {
    if (shouldNotifyListeners()) {
      final List<Trace> traces = new LinkedList<>(tracesToNotify);
      tracesToNotify.clear();
      notifyListeners(traces);
    }
  }

  private synchronized boolean shouldNotifyListeners() {
    long now = timeProvider.getCurrentTimeMillis();
    long timeFromLastNotification = now - lastNotificationTime;
    boolean hasTracesToNotify = tracesToNotify.size() > 0;
    return timeFromLastNotification > logReaderConfig.getSamplingRate() && hasTracesToNotify;
  }

  private synchronized void notifyListeners(final List<Trace> traces) {
    mainThread.post(new Runnable() {
      @Override public void run() {
        for (Listener listener : listeners) {
          String logTrace = listener.onNewTraces(traces);
          Log.d(LOGTAG, logTrace);
        }
        lastNotificationTime = timeProvider.getCurrentTimeMillis();
      }
    });
  }

  public interface Listener {
    String onNewTraces(List<Trace> traces);
  }
}
