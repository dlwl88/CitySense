package com.hmdp.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.BLOG_LIKE_DIRTY_KEY;
import static com.hmdp.utils.RedisConstants.BLOG_LIKE_META_KEY;

@Component
public class BlogLikeSyncJob {
    private static final int BATCH_SIZE = 500;
    private static final String SQL = """
            UPDATE tb_blog SET liked = ?, like_version = ?
            WHERE id = ? AND like_version < ?
            """;
    private static final DefaultRedisScript<Long> ACK_SCRIPT;
    static {
        ACK_SCRIPT = new DefaultRedisScript<>();
        ACK_SCRIPT.setLocation(new ClassPathResource("blog_like_ack.lua"));
        ACK_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RedissonClient redisson;

    public BlogLikeSyncJob(StringRedisTemplate redis, JdbcTemplate jdbc,
                          PlatformTransactionManager transactionManager, RedissonClient redisson) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.redisson = redisson;
    }

    private record Snapshot(long id, int count, long version) {}

    @XxlJob("blogLikeSyncJob")
    public void execute() throws Exception {
        RLock lock = redisson.getLock("lock:blog:like:sync");
        if (!lock.tryLock()) return;
        try {
            List<Snapshot> batch = new ArrayList<>(BATCH_SIZE);
            try (Cursor<Map.Entry<String, String>> cursor = redis.<String, String>opsForHash()
                    .scan(BLOG_LIKE_DIRTY_KEY, ScanOptions.scanOptions().count(BATCH_SIZE).build())) {
                while (cursor.hasNext()) {
                    long id = Long.parseLong(cursor.next().getKey());
                    // 单篇笔记一次 HGETALL，计数与版本来自同一 Redis 状态。
                    Map<String, String> meta = redis.<String, String>opsForHash()
                            .entries(BLOG_LIKE_META_KEY + id);
                    if (!meta.containsKey("count") || !meta.containsKey("version")) {
                        throw new IllegalStateException("待同步点赞快照缺失，blogId=" + id);
                    }
                    batch.add(new Snapshot(id, Integer.parseInt(meta.get("count")),
                            Long.parseLong(meta.get("version"))));
                    if (batch.size() == BATCH_SIZE) {
                        flush(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) flush(batch);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void flush(List<Snapshot> batch) {
        transaction.executeWithoutResult(status -> jdbc.batchUpdate(SQL, batch, BATCH_SIZE, (ps, row) -> {
            ps.setInt(1, row.count());
            ps.setLong(2, row.version());
            ps.setLong(3, row.id());
            ps.setLong(4, row.version());
        }));
        // 事务提交后才确认；失败时保留标记，重复执行由版本条件保护。
        for (Snapshot row : batch) {
            redis.execute(ACK_SCRIPT, List.of(BLOG_LIKE_DIRTY_KEY),
                    Long.toString(row.id()), Long.toString(row.version()));
        }
    }
}
