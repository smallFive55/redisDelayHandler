package com.five.delay.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 延时任务处理器注解
 * @author luopeng
 * @date 2021-12-31 14:42
 * @remark
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DelayListener {

    String name();

    /**
     * 任务轮询的三种模型
     *  public模型：公共轮询线程，key等于delay.task.public
     *  customize模型：单线程轮询（自定义轮询线程），获取数据后，根据任务类型分别执行处理器Handler
     *  exclusive模型(默认)：每种任务类型分别有一个线程轮询，并调用各类型的处理器Handler执行任务
     * @return
     */
    String mode() default "exclusive";

    /**
     * 指定延迟任务存放key，public与customize模型时，可指定延迟任务消息存放的key，当exclusive与customize模型时，key 默认等于 name
     * public模型未配置key时，key 等于 delay.task.public
     * @return
     */
    String key() default "";

}
