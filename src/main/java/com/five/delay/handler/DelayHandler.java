package com.five.delay.handler;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @conditional
 *  如果当前环境存在redisTemplate Bean时，执行延迟任务处理器
 * @author luopeng
 * @date 2021-12-30 16:34
 * @remark
 */
@Component
@ConditionalOnBean(name = "redisTemplate")
@ConfigurationProperties(prefix = "delay.handler")
public class DelayHandler {
    private static Logger logger = LoggerFactory.getLogger(DelayHandler.class);

    // 本地轮询任务初始化延迟时长
    private int initialDelay = 1000;
    // 本地轮询间隙时长
    private int period = 1000;
    // 本地轮询任务核心线程数
    private int corePoolSize = 10;
    // 本地轮询任务线程名前缀
    private String threadPrefix = "sync-five.delayHandler-pool";

    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    // 针对本地轮询的key（即队列名），确定下一次轮询的元素个数
    private ConcurrentHashMap<String, Integer> zrangQuantityMap = new ConcurrentHashMap<String, Integer>();

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private DelayHandlerProcessor delayHandlerProcessor;

    /**
     * 任务轮询器线程池，调用处理线程执行具体任务
     * 分开的目的是避免因为任务执行耽误轮询效率
     */
    private ScheduledThreadPoolExecutor executorService;

    public void poll(String key) {

        if (executorService == null) {
            synchronized (this) {
                if (executorService == null) {
                    executorService = new ScheduledThreadPoolExecutor(corePoolSize, new ThreadFactoryBuilder().setNamePrefix(threadPrefix + "-").build());
                }
            }
        }
        executorService.scheduleAtFixedRate(new PollWorker(key), initialDelay, period, timeUnit);
    }

    class PollWorker implements Runnable {
        String key;

        public PollWorker(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            int repeat = 0;
            int quantity = 9;
            if (!zrangQuantityMap.isEmpty() && zrangQuantityMap.containsKey(key)) {
                quantity = zrangQuantityMap.get(key);
            }
            try {
                Set<ZSetOperations.TypedTuple<Element>> zrangeWithScores = redisTemplate.opsForZSet().rangeWithScores(key, 0, quantity);
                // 判断元素是否超时  根据超时时间戳
                if(zrangeWithScores !=null && !zrangeWithScores.isEmpty()){
                    for (int i = 0; i < zrangeWithScores.toArray().length; i++) {
                        // 获取任务元素信息
                        ZSetOperations.TypedTuple<Element> typedTuple = (ZSetOperations.TypedTuple<Element>) (zrangeWithScores.toArray()[i]);
                        Element element = typedTuple.getValue();
                        // 判断本地服务是否能够消费该消息？主要是default、customize两钟模式下可能无法消费该消息的情况
                        if (!delayHandlerProcessor.delayListenerEndpoints.containsKey(element.getDelayName())) {
                            repeat++;
                        } else if (typedTuple.getScore() <= System.currentTimeMillis()) {
                            // 处理超时消息
                            processTimeout(key, typedTuple);
                        }
                    }
                } else {
                    // 表示当前队列尾空队列，可以适当降低轮询评率
                    logger.info(Thread.currentThread().getName()+"表示当前队列尾空队列，可以适当降低轮询频率");
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.error(Thread.currentThread().getName()+"轮询任务执行异常："+e.getMessage());
            }
            // 更新range quantity值
            if (repeat > 0) {
                zrangQuantityMap.put(key, repeat + 9);
            } else {
                zrangQuantityMap.remove(key);
            }
        }
    }

    /**
     * 处理超时消息
     * @param key
     * @param typedTuple
     */
    void processTimeout(String key, ZSetOperations.TypedTuple<Element> typedTuple){
        Element element = typedTuple.getValue();
        Long zrem = redisTemplate.opsForZSet().remove(key, element);
        if(zrem!=null && zrem>0){
            // 如果元素删除成功，表示任务被当前节点处理
            delayHandlerProcessor.process(element.getDelayName(), key, typedTuple);
        }
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public void setThreadPrefix(String threadPrefix) {
        this.threadPrefix = threadPrefix;
    }

}
