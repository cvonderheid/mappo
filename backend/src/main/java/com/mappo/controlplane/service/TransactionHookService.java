package com.mappo.controlplane.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class TransactionHookService {

    public void afterCommitOrNow(Runnable action) {
        if (
            TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()
        ) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
            return;
        }
        runSafely(action);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            log.error("Post-commit hook failed", error);
            throw error;
        }
    }
}
