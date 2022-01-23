package com.five.delay.handler;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import com.five.delay.annotation.DelayListener;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.handler.bean.DelayElement;
import com.five.delay.utils.CalendarUtils;
import com.five.delay.utils.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 通过Spring容器获取被 {@link DelayListener} 注解的延时任务处理器，并执行处理器调用
 *
 * @author luopeng
 * @date 2021-12-31 15:10
 * @remark
 */
@Component
public class DelayHandlerProcessor implements CommandLineRunner {
    private static Logger logger = LoggerFactory.getLogger(DelayHandlerProcessor.class);

    // 延迟任务执行器，待优化
    private ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder() .setNamePrefix("test-").build());
    // 本地 DelayListener 封装集合
    protected Set<String> delays = new HashSet<String>();
    // 根据本地 DelayListener 配置获取所有key
    private Set<String> keys = new HashSet<String>();
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        // 启动时通过Spring容器获取延迟任务
        ApplicationContext applicationContext = SpringContextUtils.applicationContext;
        logger.info("服务启动："+applicationContext.getId());

        if(applicationContext != null){
            Map<String,Object> beans = applicationContext.getBeansWithAnnotation(Component.class);
            for(Object bean:beans.values()){
                for (Method method : bean.getClass().getMethods()) {
                    if (method.isAnnotationPresent(DelayListener.class)) {
                        analyseDelay(method, bean);
                    }
                }
            }

            // 根据获取的延迟任务处理器，执行任务轮询
            if (!keys.isEmpty()) {
                DelayHandler handler = applicationContext.getBean(DelayHandler.class);
                Iterator<String> iterator = keys.iterator();
                while(iterator.hasNext()) {
                    handler.poll(iterator.next());
                }
            }
        }
    }

    /**
     * 解析延迟任务方法配置
     * @param method
     * @param bean
     * @return 任务名
     * @throws Exception
     */
    private String analyseDelay(Method method, Object bean) throws Exception {
        DelayListener delayListener = method.getAnnotation(DelayListener.class);
        String delayName = delayListener.name();
        String mode = delayListener.mode();
        String key = delayListener.key();
        int retry = delayListener.retry();
        int retryDelay = delayListener.retryDelay();

        // 1.delayName应该是独一无二的
        if (delays.contains(delayName)) {
            throw new Exception("不允许在本地配置相同的延迟任务名[\"+delayName+\"]");
        }

        if (StrUtil.isEmpty(mode) || mode.equals(DelayPollModeConf.MODE_EXCLUSIVE)) {
            // 独立的轮询线程
            key = delayName;
        } else if (mode.equals(DelayPollModeConf.MODE_CUSTOMIZE)) {
            if (StrUtil.isEmpty(key)) {
                // 自定义模式，默认key等于任务名
                key = delayName;
            }
        } else if (mode.equals(DelayPollModeConf.MODE_PUBLIC)) {
            if (StrUtil.isEmpty(key)) {
                // 公共轮询任务
                key = DelayPollModeConf.PUBLIC_MODE_KEY;
            }
        } else {
            throw new Exception("延迟任务["+delayName+"]mode类型配置错误！");
        }
        // 保证在不同的服务中，延迟任务全局只有唯一的消费窗口，允许在同一服务的不同节点消费
        String contextId = SpringContextUtils.applicationContext.getId();

        MethodDelayHandlerEndpoint endpoint = (MethodDelayHandlerEndpoint) redisTemplate.opsForHash().get(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, delayName);
        if (endpoint != null && StrUtil.isNotEmpty(endpoint.getConsumeContextId()) && !contextId.equals(endpoint.getConsumeContextId())){
            // 存在相同的delayName，且contextId不同时，错误。如果是由于主动修改了contextId导致，可以删除delay.application.map中指定的K/V
            throw new Exception("延迟任务[" + delayName + "]在服务"+endpoint.getConsumeContextId()+"中已存在消费窗口！");
        }
        keys.add(key);
        endpoint = new MethodDelayHandlerEndpoint(delayName, key, retry, retryDelay, contextId, method.getName(), method.getParameterTypes(), bean);
        delays.add(delayName);
        redisTemplate.opsForHash().put(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, delayName, endpoint);
        return delayName;
    }

    public void process(String key, ZSetOperations.TypedTuple<DelayElement>  typedTuple){
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                DelayElement element = typedTuple.getValue();
                try {
                    MethodDelayHandlerEndpoint delayHandlerEndpoint = (MethodDelayHandlerEndpoint) redisTemplate.opsForHash().get(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, element.getDelayName());
                    Method method = delayHandlerEndpoint.getObj().getClass().getMethod(delayHandlerEndpoint.getMethodName(), delayHandlerEndpoint.getParameterTypes());
                    try {
                        method.invoke(delayHandlerEndpoint.getObj(), element.getValue());
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        // 非法调用
                        if (element.getRetried() < element.getRetry() || -1 == element.getRetry()) {
                            element.setRetried(element.getRetried() + 1);
                            redisTemplate.opsForZSet().add(key, element, CalendarUtils.getCurrentTimeInMillis(element.getRetryDelay(), Calendar.MILLISECOND));
                        }
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                        // 调用异常
                        // 可配置消费失败处理策略【直接抛弃、重试次数】
                        if (element.getRetried() < element.getRetry() || -1 == element.getRetry()) {
                            element.setRetried(element.getRetried() + 1);
                            redisTemplate.opsForZSet().add(key, element, CalendarUtils.getCurrentTimeInMillis(element.getRetryDelay(), Calendar.MILLISECOND));
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理器调用异常："+e.getMessage());
                    // 可配置消费失败处理策略【直接抛弃、重试次数】
                    if (element.getRetried() < element.getRetry() || -1 == element.getRetry()) {
                        element.setRetried(element.getRetried() + 1);
                        redisTemplate.opsForZSet().add(key, element, CalendarUtils.getCurrentTimeInMillis(element.getRetryDelay(), Calendar.MILLISECOND));
                    }
                }
            }
        },0, TimeUnit.MILLISECONDS);
    }

}
