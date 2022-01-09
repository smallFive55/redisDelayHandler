package com.five.delay.handler;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.StrUtil;
import com.five.delay.annotation.DelayListener;
import com.five.delay.conf.DelayPollModeConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
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
public class DelayHandlerProcessor implements ApplicationListener<ContextRefreshedEvent> {
    private static Logger logger = LoggerFactory.getLogger(DelayHandlerProcessor.class);

    // 延迟任务与信息存储的key的映射
    public HashMap<String, String> delayKeyMaps = new HashMap<String, String>();
    // 延迟任务执行器，待优化
    private ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(10, new ThreadFactoryBuilder() .setNamePrefix("test-").build());
    // DelayListener 封装集合
    private HashMap<String, MethodDelayHandlerEndpoint> delayListenerEndpoints = new HashMap<String, MethodDelayHandlerEndpoint>();
    // 根据 DelayListener 配置获取所有key
    private Set<String> keys = new HashSet<String>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 启动时通过Spring容器获取延迟任务
        ApplicationContext applicationContext = event.getApplicationContext();
        if(applicationContext.getParent()==null){
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
     */
    private void analyseDelay(Method method, Object bean) {
        DelayListener delayListener = method.getAnnotation(DelayListener.class);
        String delayName = delayListener.name();
        String mode = delayListener.mode();
        String key = delayListener.key();

        if (StrUtil.isEmpty(mode) || mode.equals(DelayPollModeConf.MODE_EXCLUSIVE)) {
            // 独立的轮询线程
            key = delayName;
        } else if (mode.equals(DelayPollModeConf.MODE_CUSTOMIZE) && StrUtil.isEmpty(key)) {
            // 自定义模式，默认key等于任务名
            key = delayName;
        } else if (mode.equals(DelayPollModeConf.MODE_PUBLIC) && StrUtil.isEmpty(key)) {
            // 公共轮询任务
            key = DelayPollModeConf.PUBLIC_MODE_KEY;
        } else {
            logger.error("mode配置错误");
        }
        keys.add(key);
        delayKeyMaps.put(delayName, key);
        delayListenerEndpoints.put(delayName, new MethodDelayHandlerEndpoint(delayName, key, method, bean));
    }

    public void process(String delayName, Object value){
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    Method method = delayListenerEndpoints.get(delayName).getMethod();
                    try {
                        method.invoke(delayListenerEndpoints.get(delayName).getObj(), value);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    logger.error("处理器调用异常："+e.getMessage());
                }
            }
        },0, TimeUnit.MILLISECONDS);
    }
}
