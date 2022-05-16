package com.five.delay.handler;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public interface DelayParser {

    /**
     * 本地 DelayListener 封装集合
     */
    Set<String> delays = new HashSet<String>();

    /**
     * 解析延迟任务方法配置
     * @param method @DelayListener注解的方法
     * @param bean   方法所在 object bean
     * @return key
     * @exception
     */
    String analyseDelay(Method method, Object bean) throws Exception;

    /**
     * 配置key中任务的最小延期时间
     * @param key 任务key
     * @param delay 延迟时间(ms 毫秒)
     */
    void setTaskMinDelay(String key, int delay);

    /**
     * 设置key上所有任务的轮询频率
     * @param key 任务key
     * @param rate 轮询频率(ms 毫秒)
     */
    void setTaskRate(String key, int rate);

    /**
     * 计算空任务轮询频率 emptyRate
     * @param key   任务key
     * @return      emptyRate
     */
    Integer calculationEmptyRate(String key);
}
