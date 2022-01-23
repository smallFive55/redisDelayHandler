package com.five.delay.handler.bean;

import java.io.Serializable;

/**
 * zSet值封装
 * @author luopeng
 * @date 2022-01-02 22:43
 * @remark
 */
public class DelayElement implements Serializable {

    private String delayName;

    private Object value;
    /**
     * 配置任务处理失败重试次数，-1表示一直重试
     */
    private int retry;

    /**
     * 已重试次数
     */
    private int retried;

    /**
     * 配置任务处理失败重试间隔时间，单位为毫秒
     */
    private int retryDelay;

    public DelayElement(){
    }

    public DelayElement(String delayName, Object value){
        this.delayName = delayName;
        this.value = value;
    }

    public DelayElement(String delayName, Object value, int retry, int retryDelay){
        this.delayName = delayName;
        this.value = value;
        this.retry = retry;
        this.retryDelay = retryDelay;
    }

    public String getDelayName() {
        return delayName;
    }

    public void setDelayName(String delayName) {
        this.delayName = delayName;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public int getRetried() {
        return retried;
    }

    public void setRetried(int retried) {
        this.retried = retried;
    }
}
