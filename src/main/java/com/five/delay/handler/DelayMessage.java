package com.five.delay.handler;

import java.util.Calendar;

/**
 * @author luopeng
 * @date 2021-12-31 16:40
 * @remark
 */
public class DelayMessage<T> {
    /**
     * 延迟任务名
     */
    private String delayName;
    /**
     * 延迟任务消息体（泛型）
     */
    private T value;
    /**
     * 任务延迟时间
     */
    private int delay;
    /**
     * 任务延迟时间单位
     */
    private int calendarTimeUnit;

    public DelayMessage(){
    }

    public DelayMessage(String delayName, T value, int delay){
        this(delayName, value, delay, Calendar.SECOND);
    }

    public DelayMessage(String delayName, T value, int delay, int calendarTimeUnit){
        this.delayName = delayName;
        this.value = value;
        this.delay = delay;
        this.calendarTimeUnit = calendarTimeUnit;
    }

    public String getDelayName() {
        return delayName;
    }

    public void setDelayName(String delayName) {
        this.delayName = delayName;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getCalendarTimeUnit() {
        return calendarTimeUnit;
    }

    public void setCalendarTimeUnit(int calendarTimeUnit) {
            this.calendarTimeUnit = calendarTimeUnit;
        }
}
