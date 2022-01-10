package com.five.delay.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.handler.DelayHandlerProcessor;
import com.five.delay.handler.DelayMessage;
import com.five.delay.handler.Element;
import com.five.delay.service.DelayMessageService;
import com.five.delay.utils.CalendarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;

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
    private DelayHandlerProcessor delayHandlerProcessor;

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
        Object key = redisTemplate.opsForHash().get(DelayPollModeConf.DELAY_KEYS_MAP_KEY, delayMessage.getDelayName());
        if (null == key) {
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
        }
        try {
            // Element 序列化
            Element value = new Element(delayMessage.getDelayName(), delayMessage.getValue());
            redisTemplate.opsForZSet().add(String.valueOf(key), value, CalendarUtils.getCurrentTimeInMillis(delayMessage.getDelay(), delayMessage.getCalendarTimeUnit()));
            logger.info(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss")+"延迟任务添加成功..."+delayMessage);
        } catch (Exception e) {
            logger.error("延迟任务添加失败..."+e.getMessage());
        }
    }

}
