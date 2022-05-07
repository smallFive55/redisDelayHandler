package com.five.delay.handler;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.handler.bean.DelayElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

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

    /**
     * 本地轮询任务初始化延迟时长
     */
    private int initialDelay = 1000;
    /**
     * 本地轮询间隙时长
     */
    private int period = 1000;
    /**
     * 本地轮询任务核心线程数
     */
    private int corePoolSize = 10;
    /**
     * 每次轮询任务从redis中取出数据条数
     */
    private int batchSize = 10;
    /**
     * 本地轮询任务线程名前缀
     */
    private String threadPrefix = "sync-five.delayHandler-pool";

    /**
     * 自定义队列轮询频率(毫秒)
     * delayRate:
     *   key1: 1000
     *   key2: 2000
     */
    private Map<String, Integer> delayRate = new LinkedHashMap<String, Integer>();

    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 针对本地轮询的key（即队列名），确定下一次轮询的元素个数
     */
    private ConcurrentHashMap<String, Integer> zrangQuantityMap = new ConcurrentHashMap<String, Integer>();

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private DelayHandlerProcessor delayHandlerProcessor;

    /**
     * 任务轮询器线程池，调用处理线程执行具体任务
     * 分开的目的是避免因为任务执行耽误轮询效率
     */
    private ScheduledExecutorService scheduler;

    public DelayHandler() {

    }

    public void poll(String key) {

        if (scheduler == null) {
            synchronized (this) {
                if (scheduler == null) {
                    scheduler = Executors.newScheduledThreadPool(corePoolSize,
                            new ThreadFactoryBuilder()
                                    .setNamePrefix(threadPrefix + "-")
                                    .setDaemon(true)
                                    .build());
                }
            }
        }

        scheduler.schedule(new PollWorker(DelayPollModeConf.PUBLIC_MODE_KEY_PREFIX + key), getPeriod(key), timeUnit);
    }

    class PollWorker extends TimerTask implements Runnable {
        String key;

        public PollWorker(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            int rate = getPeriod(key.replace(DelayPollModeConf.PUBLIC_MODE_KEY_PREFIX, ""));
            try {
                int repeat = 0;
                int quantity = batchSize - 1;
                if (!zrangQuantityMap.isEmpty() && zrangQuantityMap.containsKey(key)) {
                    quantity = zrangQuantityMap.get(key);
                }
                try {
                    Set<ZSetOperations.TypedTuple<DelayElement>> zrangeWithScores = redisTemplate.opsForZSet().rangeWithScores(key, 0, quantity);
                    if(zrangeWithScores !=null && !zrangeWithScores.isEmpty()){
                        for (int i = 0; i < zrangeWithScores.toArray().length; i++) {
                            // 获取任务元素信息
                            ZSetOperations.TypedTuple<DelayElement> typedTuple = (ZSetOperations.TypedTuple<DelayElement>) (zrangeWithScores.toArray()[i]);
                            // 先根据超时时间戳判断元素是否超时
                            if (typedTuple.getScore() <= System.currentTimeMillis()) {
                                DelayElement element = typedTuple.getValue();
                                // 判断本地服务是否能够消费该消息，由于default、customize两种模式下可能包含本地服务无法消费的消息
                                if (!delayHandlerProcessor.delays.contains(element.getDelayName())) {
                                    repeat++;
                                } else {
                                    //处理超时任务
                                    processDelayTask(key, typedTuple);
                                }
                            }
                        }
                    } else {
                        // TODO 表示当前队列尾空队列，可以适当降低轮询频率

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error(Thread.currentThread().getName()+"轮询任务执行异常："+e.getMessage());
                }
                // 更新range quantity值
                if (repeat > 0) {
                    zrangQuantityMap.put(key, repeat + batchSize - 1);
                } else {
                    zrangQuantityMap.remove(key);
                }
            } finally {
                scheduler.schedule(this, rate, timeUnit);
            }
        }
    }

    /**
     * 处理超时消息
     * @param key
     * @param typedTuple
     */
    void processDelayTask(String key, ZSetOperations.TypedTuple<DelayElement> typedTuple){
        DelayElement element = typedTuple.getValue();
        Long zrem = redisTemplate.opsForZSet().remove(key, element);
        if(zrem!=null && zrem>0){
            // 如果元素删除成功，表示任务被当前节点处理
            delayHandlerProcessor.process(key, typedTuple);
        }
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public int getPeriod(String key) {
        if (null != key && !delayRate.isEmpty()){
            Integer rate = delayRate.get(key);
            if (rate != null && rate > 0) {
                return rate;
            }
        }
        return period;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public void setThreadPrefix(String threadPrefix) {
        this.threadPrefix = threadPrefix;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setDelayRate(Map<String, Integer> delayRate) {
        this.delayRate = delayRate;
    }
}
