package com.datadog.logcat_reader.presenter;

import java.util.LinkedList;
import java.util.List;

import com.datadog.logcat_reader.model.Trace;

/**
 * Buffer created to keep a max number of traces and be able to configure the size of the buffer.
 */
class TraceBuffer {

  private int bufferSize;
  private final List<Trace> traces;

  TraceBuffer(int bufferSize) {
    this.bufferSize = bufferSize;
    traces = new LinkedList<Trace>();
  }

  /**
   * Configures the max number of traces to keep inside the buffer
   */
  void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
    removeExceededTracesIfNeeded();
  }

  /**
   * Adds a list of traces to the buffer, if the buffer is full your new traces will be added and
   * the previous one will be removed.
   */
  int add(List<Trace> traces) {
    this.traces.addAll(traces);
    return removeExceededTracesIfNeeded();
  }

  /**
   * Returns the current list of traces stored in the buffer.
   */
  List<Trace> getTraces() {
    return traces;
  }

  /**
   * Returns the number of traes stored in the buffer.
   */
  public int getCurrentNumberOfTraces() {
    return traces.size();
  }

  /**
   * Removes traces stored in the buffer.
   */
  public void clear() {
    traces.clear();
  }

  private int removeExceededTracesIfNeeded() {
    int tracesToDiscard = getNumberOfTracesToDiscard();
    if (tracesToDiscard > 0) {
      discardTraces(tracesToDiscard);
    }
    return tracesToDiscard;
  }

  private int getNumberOfTracesToDiscard() {
    int currentTracesSize = this.traces.size();
    int tracesToDiscard = currentTracesSize - bufferSize;
    tracesToDiscard = tracesToDiscard < 0 ? 0 : tracesToDiscard;
    return tracesToDiscard;
  }

  private void discardTraces(int tracesToDiscard) {
    for (int i = 0; i < tracesToDiscard; i++) {
      traces.remove(0);
    }
  }
}
