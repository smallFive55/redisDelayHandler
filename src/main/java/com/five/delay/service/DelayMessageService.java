package com.five.delay.service;

import com.five.delay.handler.DelayMessage;

/**
 * @author luopeng
 * @date 2021-12-31 17:10
 * @remark
 */
public interface DelayMessageService {

    /**
     * 发送延迟任务消息，默认模式
     * @param delayMessage
     * @throws Exception
     */
    void sendMessage(DelayMessage delayMessage) throws Exception;

    /**
     * 发送延迟任务消息，默认模式
     * @param delayMessage
     * @throws Exception
     */
    void sendMessage(DelayMessage delayMessage, String pollMode) throws Exception;

    /**
     * 发送延迟任务消息，指定模式、key
     * @param delayMessage
     * @param pollMode
     * @param key
     * @throws Exception
     */
    void sendMessage(DelayMessage delayMessage, String pollMode, String key) throws Exception;
}
