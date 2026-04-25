package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class MessageHandler<T> {
    private final StringRedisTemplate stringRedisTemplate;
    private ExecutorService executor;
    private String streamName;
    private String group;
    private String consumer;
    private java.util.function.Consumer<T> function;
    private Class<T> clazz;

    private volatile boolean running = true;

    private static final int BATCH_SIZE = 10;
    private static final Duration PENDING_READ_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration NEW_MESSAGE_READ_TIMEOUT = Duration.ofSeconds(2);

    public MessageHandler(StringRedisTemplate stringRedisTemplate,
                          java.util.function.Consumer<T> function,
                          Class<T> clazz,
                          String streamName,
                          String group,
                          String consumer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamName = streamName;
        this.group = group;
        this.consumer = consumer;
        this.function = function;
        this.clazz = clazz;

        executor = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().availableProcessors() * 2,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 启动消息消费循环
     * 优先处理 pending 消息，再处理新消息
     */
    public void run() {
        while (running) {
            try {
                handlePendingMessages();
                handleNewMessages();
            } catch (Exception e) {
                log.error("消息处理异常", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 处理 pending 消息（未 ACK 的旧消息）
     * 使用较短的超时时间，快速检查是否有遗留消息
     */
    private void handlePendingMessages() {
        try {
            List<MapRecord<String, Object, Object>> pendingList = stringRedisTemplate.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(BATCH_SIZE).block(PENDING_READ_TIMEOUT),
                    StreamOffset.create(streamName, ReadOffset.from("0"))
            );

            if (pendingList != null && !pendingList.isEmpty()) {
                log.debug("发现 {} 条pending消息", pendingList.size());
                for (MapRecord<String, Object, Object> record : pendingList) {
                    processRecord(record);
                }
            }
        } catch (Exception e) {
            log.warn("处理pending消息失败", e);
        }
    }

    /**
     * 处理新消息
     * 使用较长的超时时间，阻塞等待新消息到达
     */
    private void handleNewMessages() {
        try {
            List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(BATCH_SIZE).block(NEW_MESSAGE_READ_TIMEOUT),
                    StreamOffset.create(streamName, ReadOffset.lastConsumed())
            );

            if (list != null && !list.isEmpty()) {
                for (MapRecord<String, Object, Object> record : list) {
                    processRecord(record);
                }
            }
        } catch (Exception e) {
            log.warn("读取新消息失败", e);
        }
    }

    /**
     * 处理单条消息记录
     * 1. 将 Map 数据转换为业务对象
     * 2. 提交到线程池异步执行业务逻辑
     * 3. 业务成功后进行 ACK 确认
     */
    private void processRecord(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> value = record.getValue();

            T obj = BeanUtil.mapToBean(value, clazz, false);

            executor.execute(() -> {
                try {
                    function.accept(obj);

                    stringRedisTemplate.opsForStream().acknowledge(streamName, group, record.getId());
                    log.debug("消息处理成功并ACK: {}", record.getId());
                } catch (Exception e) {
                    log.error("业务处理失败，消息将保留在pending列表: {}", record.getId(), e);
                }
            });
        } catch (Exception e) {
            log.error("消息解析失败: {}", record.getId(), e);
        }
    }

    /**
     * 优雅关闭消息处理器
     * 1. 停止接收新消息
     * 2. 等待已提交的任务完成（最多10秒）
     * 3. 超时后强制关闭线程池
     */
    public void shutdown() {
        running = false;

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("线程池未在10秒内关闭，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("关闭线程池时被中断，强制关闭");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
