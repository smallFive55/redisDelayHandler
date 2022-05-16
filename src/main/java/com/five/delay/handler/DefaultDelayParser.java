package com.five.delay.handler;

import cn.hutool.core.util.StrUtil;
import com.five.delay.annotation.DelayListener;
import com.five.delay.conf.DelayPollModeConf;
import com.five.delay.utils.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 延迟任务轮询处理器
 *
 * @author luopeng
 * @date 2022-05-13 09:10
 * @remark
 */
@Component("default")
public class DefaultDelayParser implements DelayParser {
    private static Logger logger = LoggerFactory.getLogger(DefaultDelayParser.class);

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public String analyseDelay(Method method, Object bean) throws Exception {
        DelayListener delayListener = method.getAnnotation(DelayListener.class);
        String delayName = delayListener.name();
        String mode = delayListener.mode();
        String key = delayListener.task();
        int retry = delayListener.retry();
        int retryDelay = delayListener.retryDelay();

        // 1.delayName应该是独一无二的
        if (delays.contains(delayName)) {
            throw new Exception("不允许在本地配置相同的延迟任务名["+delayName+"]");
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
            throw new Exception("延迟任务[" + delayName + "]在"+endpoint.getConsumeContextId()+"服务中已存在消费窗口！");
        }
        endpoint = new MethodDelayHandlerEndpoint(delayName, key, retry, retryDelay, contextId, method.getName(), method.getParameterTypes(), bean);
        delays.add(delayName);
        redisTemplate.opsForHash().put(DelayPollModeConf.DELAY_METADATA_HANDLER_MAP, delayName, endpoint);
        return key;
    }

    /**
     * 校验、配置key
     * @param key 任务key
     * @param period key配置的默认轮询间隙时长
     * @return 任务key 对应的配置key
     */
    private String verify(String key, int period){
        String configKey = key + ".config";
        // TODO 是否需要考虑并发情况？
        if (!redisTemplate.hasKey(configKey)) {
            // for modify [3] (1:配置变更；0:配置无变更)
            redisTemplate.opsForList().leftPush(configKey, 1);
            // for empty rate [2]
            redisTemplate.opsForList().leftPush(configKey, Integer.MAX_VALUE);
            // for rate [1]
            redisTemplate.opsForList().leftPush(configKey, period);
            // for delay [0]
            redisTemplate.opsForList().leftPush(configKey, Integer.MAX_VALUE);
        }
        return configKey;
    }

    @Override
    public void setTaskMinDelay(String key, int delay) {
        String configKey = verify(key, delay);
        int delayValue = Integer.valueOf((Integer) redisTemplate.opsForList().index(configKey, 0));
        if(delay < delayValue) {
            redisTemplate.opsForList().set(configKey, 0, delay);
            redisTemplate.opsForList().set(configKey, 3, 1);
        }
    }

    /**
     * 获取key中任务的最小延期时间
     * @param configList 配置信息List
     * @return key中任务的最小延期时间
     */
    private int getTaskMinDelay(List configList) {
        if (configList == null || configList.isEmpty()) {
            return -1;
        }
        return (Integer)configList.get(0);
    }

    @Override
    public void setTaskRate(String key, int rate) {
        String configKey = verify(key, rate);
        int rateValue = Integer.valueOf((Integer) redisTemplate.opsForList().index(configKey, 1));
        if(rate < rateValue) {
            redisTemplate.opsForList().set(configKey, 1, rate);
            redisTemplate.opsForList().set(configKey, 3, 1);
        }
    }

    /**
     * 获取key上所有任务的轮询频率
     * @param configList 配置信息List
     * @return key上所有任务的轮询频率
     */
    private int getTaskRate(List configList) {
        if (configList == null || configList.isEmpty()) {
            return -1;
        }
        return (Integer)configList.get(1);
    }

    /**
     * 获取key当前空任务轮询频率
     * @param configList 配置信息List
     * @return key当前空任务轮询频率
     */
    private int getEmptyRate(List configList) {
        if (configList == null || configList.isEmpty()) {
            return -1;
        }
        return (Integer)configList.get(2);
    }

    /**
     * 配置是否变更
     * 配置变更表示空任务轮询频率需要重新计算
     * @param configList 配置信息List
     * @return 变更标志 (1:配置变更；0:配置无变更)
     */
    private boolean isChange(List configList) {
        if (configList == null || configList.isEmpty()) {
            return false;
        }
        int tag = (Integer)configList.get(3);
        if (tag == 1) {
            return true;
        } else if (tag == 0) {
            return false;
        } else {
            return false;
        }
    }

    public List getConfigList(String key){
        String configKey = verify(key, 0);
        List configList = redisTemplate.opsForList().range(configKey, 0, -1);
        return configList;
    }

    @Override
    public Integer calculationEmptyRate(String key) {
        // 判断配置是否变更过，未变更则不重新计算空任务轮询频率
        List configList = getConfigList(key);
        boolean change = isChange(configList);
        if(!change) {
            int emptyRate = getEmptyRate(configList);
            return emptyRate;
        }
        int taskMinDelay = getTaskMinDelay(configList);
        int taskRate = getTaskRate(configList);
        if (taskMinDelay == Integer.MAX_VALUE) {
            // key中未插入延迟消息任务，不知道实际的消息最小延迟时间。直接使用当前轮询时间
            return taskRate;
        }

        // 空任务轮询频率算法
        int rate = (taskMinDelay - taskRate + 2);
        int emptyRate = getEmptyRate(configList);
        if (rate < emptyRate) {
            logger.info("根据计算结果，重新计算emptyRate值："+rate+"，原emptyRate值为："+emptyRate);
            String configKey = verify(key, 0);
            redisTemplate.opsForList().set(configKey, 2, rate);
            redisTemplate.opsForList().set(configKey, 3, 0);
            emptyRate = rate;
        }
        return emptyRate;
    }
}
