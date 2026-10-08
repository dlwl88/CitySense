package com.hmdp.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.BLOG_LIKE_DIRTY_KEY;
import static com.hmdp.utils.RedisConstants.BLOG_LIKE_META_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlogLikeSyncJobTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, String, String> hashes = mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final Cursor<Map.Entry<String, String>> cursor = mock(Cursor.class);
    private BlogLikeSyncJob job;

    @BeforeEach
    void setUp() {
        when(redisson.getLock("lock:blog:like:sync")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(redis.<String, String>opsForHash()).thenReturn(hashes);
        when(hashes.scan(eq(BLOG_LIKE_DIRTY_KEY), any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(Map.entry("123", "19"));
        when(hashes.entries(BLOG_LIKE_META_KEY + "123"))
                .thenReturn(Map.of("count", "100", "version", "20"));
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        job = new BlogLikeSyncJob(redis, jdbc, transactions, redisson);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void writesLatestSnapshotAndAcknowledgesOnlyAfterCommit() throws Exception {
        doAnswer(invocation -> {
            assertTrue(invocation.<String>getArgument(0).contains("like_version < ?"));
            Collection rows = invocation.getArgument(1);
            assertEquals(1, rows.size());
            PreparedStatement statement = mock(PreparedStatement.class);
            ParameterizedPreparedStatementSetter setter = invocation.getArgument(3);
            setter.setValues(statement, rows.iterator().next());
            verify(statement).setInt(1, 100);
            verify(statement).setLong(2, 20L);
            verify(statement).setLong(3, 123L);
            verify(statement).setLong(4, 20L);
            return new int[][]{{1}};
        }).when(jdbc).batchUpdate(anyString(), anyCollection(), eq(500),
                any(ParameterizedPreparedStatementSetter.class));

        job.execute();

        var order = inOrder(transactions, redis, lock);
        order.verify(transactions).commit(any(TransactionStatus.class));
        order.verify(redis).execute(any(RedisScript.class), eq(List.of(BLOG_LIKE_DIRTY_KEY)),
                eq("123"), eq("20"));
        order.verify(lock).unlock();
        verify(cursor).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void databaseFailureRollsBackAndDoesNotAcknowledge() {
        when(jdbc.batchUpdate(anyString(), anyCollection(), eq(500),
                any(ParameterizedPreparedStatementSetter.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThrows(DataAccessResourceFailureException.class, job::execute);

        verify(transactions).rollback(any(TransactionStatus.class));
        verify(transactions, never()).commit(any(TransactionStatus.class));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(lock).unlock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void commitFailureDoesNotAcknowledge() {
        doThrow(new IllegalStateException("commit failed"))
                .when(transactions).commit(any(TransactionStatus.class));
        assertThrows(IllegalStateException.class, job::execute);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        verify(lock).unlock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgementFailurePropagatesAfterDatabaseCommit() {
        when(redis.execute(any(RedisScript.class), eq(List.of(BLOG_LIKE_DIRTY_KEY)),
                eq("123"), eq("20")))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        assertThrows(IllegalStateException.class, job::execute);
        verify(transactions).commit(any(TransactionStatus.class));
        verify(transactions, never()).rollback(any(TransactionStatus.class));
        verify(lock).unlock();
    }

    @Test
    void competingExecutorDoesNotReadOrWrite() throws Exception {
        when(lock.tryLock()).thenReturn(false);
        job.execute();
        verifyNoInteractions(jdbc, transactions);
        verify(hashes, never()).scan(anyString(), any(ScanOptions.class));
        verify(lock, never()).unlock();
    }

    @Test
    void missingSnapshotFailsWithoutLosingMarker() {
        when(hashes.entries(BLOG_LIKE_META_KEY + "123")).thenReturn(Map.of());
        assertThrows(IllegalStateException.class, job::execute);
        verifyNoInteractions(jdbc, transactions);
        verify(lock).unlock();
    }

    @Test
    @SuppressWarnings("unchecked")
    void separatesLargeDirtySetIntoBatches() throws Exception {
        int[] position = {0};
        when(cursor.hasNext()).thenAnswer(invocation -> position[0] < 501);
        when(cursor.next()).thenAnswer(invocation -> Map.entry(Long.toString(++position[0]), "1"));
        when(hashes.entries(anyString())).thenReturn(Map.of("count", "1", "version", "1"));
        job.execute();
        verify(jdbc, times(2)).batchUpdate(anyString(), anyCollection(), eq(500),
                any(ParameterizedPreparedStatementSetter.class));
        verify(transactions, times(2)).commit(any(TransactionStatus.class));
        verify(redis, times(501)).execute(any(RedisScript.class), eq(List.of(BLOG_LIKE_DIRTY_KEY)),
                anyString(), eq("1"));
    }
}
