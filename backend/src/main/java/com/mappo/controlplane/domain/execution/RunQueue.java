package com.mappo.controlplane.domain.execution;

public interface RunQueue {

    boolean isEnabled();

    void enqueue(String runId);

    void enqueue(String runId, boolean force);

    String poll();

    boolean acquireRunLease(String runId);

    boolean isRunLeaseHeld(String runId);

    void renewRunLease(String runId);

    void releaseRunLease(String runId);
}
