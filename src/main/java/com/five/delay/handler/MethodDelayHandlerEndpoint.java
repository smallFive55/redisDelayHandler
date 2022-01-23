package com.five.delay.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.five.delay.annotation.DelayListener;

import java.io.Serializable;

/**
 * 封装被 {@link DelayListener} 注解的延时任务处理器
 * @author luopeng
 * @date 2021-12-31 14:39
 * @remark
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MethodDelayHandlerEndpoint implements Serializable {

    /**
     * 延迟任务任务名称
     */
    private String delayName;
    /**
     * 延迟任务处理方法名
     */
    private String methodName;
    /**
     * 延迟任务处理方法参数
     */
    private Class<?>[] parameterTypes;
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

    /**
     * 任务消费的容器ID
     */
    private String consumeContextId;

    public MethodDelayHandlerEndpoint() {
    }

    public MethodDelayHandlerEndpoint(String delayName, String key, int retry, int retryDelay, String consumeContextId, String methodName, Class<?>[] parameterTypes, Object obj) {
        this.delayName = delayName;
        this.key = key;
        this.retry = retry;
        this.retryDelay = retryDelay;
        this.consumeContextId = consumeContextId;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.obj = obj;
    }

    public String getDelayName() {
        return delayName;
    }

    public String getMethodName() {
        return methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
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

    public String getConsumeContextId() {
        return consumeContextId;
    }

}
