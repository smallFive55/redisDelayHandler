package com.five.delay.service.impl;

import cn.hutool.core.util.StrUtil;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.handler.DelayParser;
import com.five.delay.handler.bean.DelayMessage;
import com.five.delay.handler.bean.DelayElement;
import com.five.delay.handler.MethodDelayHandlerEndpoint;
import com.five.delay.service.DelayMessageService;
import com.five.delay.utils.CalendarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @author luopeng
 * @date 2021-12-31 17:11
 * @remark
 */
@Service
public class RedisDelayMessageServiceImpl implements DelayMessageService {

    private static Logger logger = LoggerFactory.getLogger(RedisDelayMessageServiceImpl.class);

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private DelayParser delayParser;

    @Override
    public void sendMessage(DelayMessage delayMessage) throws Exception {
        sendMessage(delayMessage, DelayPollModeConf.MODE_EXCLUSIVE, null);
    }

    @Override
    public void sendMessage(DelayMessage delayMessage, String pollMode) throws Exception {
        sendMessage(delayMessage, pollMode, null);
    }

    @Override
    public void sendMessage(DelayMessage delayMessage, String pollMode, String appointKey) throws Exception {
        // 获得延迟任务配置的key
        MethodDelayHandlerEndpoint endpoint = (MethodDelayHandlerEndpoint) redisTemplate.opsForHash().get(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, delayMessage.getDelayName());
        String key;
        if (null == endpoint || StrUtil.isEmpty(endpoint.getKey())) {
            if (StrUtil.isEmpty(appointKey)) {
                if (StrUtil.isEmpty(pollMode)) {
                    // 如果未指定模式，则使用默认模型，否则使用exclusive模型独立轮询
                    pollMode = DelayPollModeConf.MODE_EXCLUSIVE;
                }
                if (DelayPollModeConf.MODE_EXCLUSIVE.equals(pollMode) || DelayPollModeConf.MODE_CUSTOMIZE.equals(pollMode)) {
                    key = delayMessage.getDelayName();
                } else if (DelayPollModeConf.MODE_PUBLIC.equals(pollMode)) {
                    key = DelayPollModeConf.PUBLIC_MODE_KEY;
                } else {
                    throw new Exception("延迟消息添加失败，错误的Mode:"+pollMode);
                }
            } else {
                key = appointKey;
            }
        } else {
            key = endpoint.getKey();
        }

        DelayElement element = new DelayElement(delayMessage.getDelayName(), delayMessage.getValue());
        if (endpoint != null) {
            element.setRetry(endpoint.getRetry());
            element.setRetryDelay(endpoint.getRetryDelay());
        }
        try {
            redisTemplate.opsForZSet().add(DelayPollModeConf.PUBLIC_MODE_KEY_PREFIX+key, element, CalendarUtils.getCurrentTimeInMillis(delayMessage.getDelay(), delayMessage.getCalendarTimeUnit()));
            // 任务添加成功，判断更新key的最小超时时间 [delay]
            delayParser.setTaskMinDelay(DelayPollModeConf.PUBLIC_MODE_KEY_PREFIX+key, delayMessage.getDelay());
        } catch (Exception e) {
            throw new Exception("延迟任务添加失败..."+e.getMessage());
        }
    }

}
