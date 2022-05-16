package com.five.delay.handler;

import com.five.delay.annotation.DelayListener;
import com.five.delay.utils.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * 延迟处理启动器
 *
 * @author luopeng
 * @date 2022-05-13 09:10
 * @remark
 */
@Component
public class DelayHandlerStarter implements CommandLineRunner {
    private static Logger logger = LoggerFactory.getLogger(DelayHandlerStarter.class);

    @Autowired
    private DelayParser delayParser;

    /**
     * 根据本地 DelayListener 配置获取所有key
     */
    private Set<String> keys = new HashSet<String>();

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
                        keys.add(delayParser.analyseDelay(method, bean));
                    }
                }
            }

            // 根据获取的延迟任务处理器，执行任务轮询
            if (!keys.isEmpty()) {
                DelayHandlerPolling handler = applicationContext.getBean(DelayHandlerPolling.class);
                Iterator<String> iterator = keys.iterator();
                while(iterator.hasNext()) {
                    handler.poll(iterator.next());
                }
            }
        }
    }
}
