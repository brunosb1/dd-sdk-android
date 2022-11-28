package com.datadog.logcat_reader.model;

/**
 * Abstraction created to represent the application main thread. This interface is used to send
 * messages from a background thread to the UI thread. The usage of interfaces to abstract the
 * execution context is really useful for testing..
 */
public interface MainThread {

  void post(Runnable runnable);
}
