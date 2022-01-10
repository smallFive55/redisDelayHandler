package com.five.delay.conf;

/**
 * 配置
 * @author luopeng
 * @date 2022-01-03 08:54
 * @remark
 */
public interface DelayPollModeConf {
    /**
     * public模型：公共轮询线程，key等于delay.task.public
     */
    String MODE_PUBLIC = "public";
    /**
     * customize模型：自定义轮询线程，获取数据后，根据任务类型分别执行处理器Handler
     */
    String MODE_CUSTOMIZE = "customize";
    /**
     * exclusive模型(默认)：独立轮询线程，获取数据后，调用各类型的处理器Handler执行任务
     */
    String MODE_EXCLUSIVE = "exclusive";

    // 公共轮询任务名
    String PUBLIC_MODE_KEY = "delay.task.public";

    /**
     * 延迟任务与信息存储的key的映射关系
     */
    String DELAY_KEYS_MAP_KEY = "delay.keys.map";
}