package com.five.delay.utils;

import org.springframework.stereotype.Component;

import java.util.Calendar;

@Component
public class CalendarUtils {
	
	/**
	 * 获得当前时间delay秒后的时间戳
	 **/
	public static long getCurrentTimeInMillis(int delay){
		Calendar cal = Calendar.getInstance();
		if(delay>0){
			cal.add(Calendar.SECOND, delay);
		}
        return cal.getTimeInMillis();
	}

	/**
	 * 获得当前时间second后的时间戳
	 **/
	public static long getCurrentTimeInMillis(int delay, int calendarTimeUnit){
		Calendar cal = Calendar.getInstance();
		if(delay>0){
			cal.add(calendarTimeUnit, delay);
		}
		return cal.getTimeInMillis();
	}

	public static String getCurrentTimeByStr(int second){
		Calendar cal = Calendar.getInstance();
		if(second>0){
			cal.add(Calendar.SECOND, second);
		}
		String time = cal.get(Calendar.HOUR_OF_DAY)+":"+cal.get(Calendar.MINUTE)+":"+cal.get(Calendar.SECOND);
		return time;
	}
}
