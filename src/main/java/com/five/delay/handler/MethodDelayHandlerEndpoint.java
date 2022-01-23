package com.five.delay.handler;

import com.five.delay.annotation.DelayListener;
import java.lang.reflect.Method;

/**
 * 封装被 {@link DelayListener} 注解的延时任务处理器
 * @author luopeng
 * @date 2021-12-31 14:39
 * @remark
 */
public class MethodDelayHandlerEndpoint {

    /**
     * 延迟任务任务名称
     */
    private String delayName;
    /**
     * 延迟任务处理方法
     */
    private Method method;
    /**
     * 任务方法所属实例
     */
    private Object obj;
    /**
     * 延迟任务消息存放的key
     */
    private String key;
    /**
     * 配置任务处理失败重试次数，-1表示一直重试
     */
    private int retry;

    /**
     * 配置任务处理失败重试间隔时间，单位为毫秒
     */
    private int retryDelay;

    public MethodDelayHandlerEndpoint(String delayName, String key, int retry, int retryDelay, Method method, Object obj) {
        this.delayName = delayName;
        this.key = key;
        this.retry = retry;
        this.retryDelay = retryDelay;
        this.method = method;
        this.obj = obj;
    }

    public Method getMethod() {
        return method;
    }

    public Object getObj() {
        return obj;
    }

    public String getKey() {
        return key;
    }

    public int getRetry() {
        return retry;
    }

    public int getRetryDelay() {
        return retryDelay;
    }
}
