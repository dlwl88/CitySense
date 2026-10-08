package com.hmdp.component;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ConversationExecutionCoordinator {

    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void runSerial(Long conversationId, Runnable task) {
        ReentrantLock lock = locks.computeIfAbsent(conversationId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            task.run();
        }
        finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                locks.remove(conversationId, lock);
            }
        }
    }
}
