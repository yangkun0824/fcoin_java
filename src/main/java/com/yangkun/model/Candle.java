package com.yangkun.model;

import java.math.BigDecimal;

// k线蜡烛
public class Candle {
	
	// 1分钟
	public static String CANDLE_RESOLUTION_M1 = "M1";
	// 3分钟
	public static String CANDLE_RESOLUTION_M3 = "M3";
	// 5分钟
	public static String CANDLE_RESOLUTION_M5 = "M5";
	// 15分钟
	public static String CANDLE_RESOLUTION_M15 = "M15";
	// 30分钟
	public static String CANDLE_RESOLUTION_M30 = "M30";
	// 1小时
	public static String CANDLE_RESOLUTION_H1 = "H1";
	// 4小时
	public static String CANDLE_RESOLUTION_H4 = "H4";
	// 6小时
	public static String CANDLE_RESOLUTION_H6 = "H6";
	// 1天
	public static String CANDLE_RESOLUTION_D1 = "D1";
	// 1周
	public static String CANDLE_RESOLUTION_W1 = "W1";
	// 1月
	public static String CANDLE_RESOLUTION_MN = "MN";
	
	private long id;
	private String resolution;
	private BigDecimal open;
	private BigDecimal close;
	private BigDecimal high;
	private BigDecimal low;
	
	
	public Candle(long id, String resolution, BigDecimal open, BigDecimal close, BigDecimal high, BigDecimal low) {
		super();
		this.id = id;
		this.resolution = resolution;
		this.open = open;
		this.close = close;
		this.high = high;
		this.low = low;
	}


	@Override
	public String toString() {
		return "Candle [id=" + id + ", resolution=" + resolution + ", open=" + open + ", close=" + close + ", high="
				+ high + ", low=" + low + "]";
	}


	public long getId() {
		return id;
	}


	public void setId(long id) {
		this.id = id;
	}


	public String getResolution() {
		return resolution;
	}


	public void setResolution(String resolution) {
		this.resolution = resolution;
	}


	public BigDecimal getOpen() {
		return open;
	}


	public void setOpen(BigDecimal open) {
		this.open = open;
	}


	public BigDecimal getClose() {
		return close;
	}


	public void setClose(BigDecimal close) {
		this.close = close;
	}


	public BigDecimal getHigh() {
		return high;
	}


	public void setHigh(BigDecimal high) {
		this.high = high;
	}


	public BigDecimal getLow() {
		return low;
	}


	public void setLow(BigDecimal low) {
		this.low = low;
	}
	
	

}
