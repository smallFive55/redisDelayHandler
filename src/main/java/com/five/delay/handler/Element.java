package com.five.delay.handler;

import java.io.Serializable;

/**
 * zSet值封装
 * @author luopeng
 * @date 2022-01-02 22:43
 * @remark
 */
public class Element implements Serializable {

    private String delayName;

    private Object value;

    public Element(){
    }

    public Element(String delayName, Object value){
        this.delayName = delayName;
        this.value = value;
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
}
