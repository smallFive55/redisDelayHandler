package com.five.delay.handler;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.five.delay.annotation.DelayListener;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.handler.bean.DelayElement;
import com.five.delay.utils.CalendarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * 通过Spring容器获取被 {@link DelayListener} 注解的延时任务处理器，并执行处理器调用
 *
 * @author luopeng
 * @date 2021-12-31 15:10
 * @remark
 */
@Component
public class DelayHandlerProcessor {
    private static Logger logger = LoggerFactory.getLogger(DelayHandlerProcessor.class);

    /**
     * 延迟任务执行器
     * 根据本地延迟任务数DelayParser.delays，任务执行器存在并发情况。且多个人五同时轮询延迟任务时，压力更大
     */
    private volatile ExecutorService executorService = null;

    @Autowired
    private RedisTemplate redisTemplate;

    public void process(String key, ZSetOperations.TypedTuple<DelayElement>  tuple){

        if (executorService == null) {
            synchronized (this) {
                if (executorService == null) {
                    int delaySize = DelayParser.delays.size();
                    executorService = new ThreadPoolExecutor(delaySize, delaySize,
                            0L, TimeUnit.MILLISECONDS,
                            new LinkedBlockingQueue<Runnable>(),
                            new ThreadFactoryBuilder().setNamePrefix("delay-process-").build());
                }
            }
        }

        this.executorService.execute(new Runnable() {
            @Override
            public void run() {
                DelayElement element = tuple.getValue();
                try {
                    MethodDelayHandlerEndpoint delayHandlerEndpoint = (MethodDelayHandlerEndpoint) redisTemplate.opsForHash().get(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, element.getDelayName());
                    Method method = delayHandlerEndpoint.getObj().getClass().getMethod(delayHandlerEndpoint.getMethodName(), delayHandlerEndpoint.getParameterTypes());
                    try {
                        method.invoke(delayHandlerEndpoint.getObj(), element.getValue());
                    } catch (IllegalAccessException e) {
                        logger.error("处理器调用异常："+e.getMessage());
                        errorProcess(element);
                    } catch (InvocationTargetException e) {
                        logger.error("处理器调用异常："+e.getMessage());
                        errorProcess(element);
                    }
                } catch (NoSuchMethodException e) {
                    logger.error("处理器调用异常："+e.getMessage());
                    errorProcess(element);
                }
            }

            private void errorProcess(DelayElement element){
                // 调用异常，可配置消费失败处理策略【直接抛弃、重试次数】
                if (element.getRetried() < element.getRetry() || -1 == element.getRetry()) {
                    element.setRetried(element.getRetried() + 1);
                    redisTemplate.opsForZSet().add(key, element, CalendarUtils.getCurrentTimeInMillis(element.getRetryDelay(), Calendar.MILLISECOND));
                }
            }

        });
    }

}
