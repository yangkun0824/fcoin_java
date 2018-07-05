package com.yangkun.model;

import java.math.BigDecimal;

import com.yangkun.fcoin.FcoinUtils;

public class Ticker {
	
	// 基准货币
	private String coinBase;
	// 定价货币
	private String coinPrice;
	// 最新成交价
	private BigDecimal marketPrice;
	// 买一价
	private BigDecimal sell1Price;
	// 买一量
	private BigDecimal sell1Volume;
	// 卖一价
	private BigDecimal buy1Price;
	// 卖一量
	private BigDecimal buy1Volume;
	// 24小时最高
	private BigDecimal high24HPrice;
	// 24小时最低
	private BigDecimal low24HPrice;
	
	public Ticker(String coinBase, String coinPrice, BigDecimal marketPrice, BigDecimal sell1Price,
			BigDecimal sell1Volume, BigDecimal buy1Price, BigDecimal buy1Volume, BigDecimal high24hPrice,
			BigDecimal low24hPrice) {
		super();
		this.coinBase = coinBase;
		this.coinPrice = coinPrice;
		this.marketPrice = marketPrice;
		this.sell1Price = sell1Price;
		this.sell1Volume = sell1Volume;
		this.buy1Price = buy1Price;
		this.buy1Volume = buy1Volume;
		this.high24HPrice = high24hPrice;
		this.low24HPrice = low24hPrice;
	}

	

	@Override
	public String toString() {
		return "Ticker [coinBase=" + coinBase + ", coinPrice=" + coinPrice + ", marketPrice=" + marketPrice
				+ ", sell1Price=" + sell1Price + ", sell1Volume=" + sell1Volume + ", buy1Price=" + buy1Price
				+ ", buy1Volume=" + buy1Volume + ", high24HPrice=" + high24HPrice + ", low24HPrice=" + low24HPrice
				+ "]";
	}
	
	

	public String getCoinBase() {
		return coinBase;
	}



	public void setCoinBase(String coinBase) {
		this.coinBase = coinBase;
	}



	public String getCoinPrice() {
		return coinPrice;
	}



	public void setCoinPrice(String coinPrice) {
		this.coinPrice = coinPrice;
	}



	public BigDecimal getMarketPrice() {
		return marketPrice;
	}



	public void setMarketPrice(BigDecimal marketPrice) {
		this.marketPrice = marketPrice;
	}



	public BigDecimal getSell1Price() {
		return sell1Price;
	}



	public void setSell1Price(BigDecimal sell1Price) {
		this.sell1Price = sell1Price;
	}



	public BigDecimal getSell1Volume() {
		return sell1Volume;
	}



	public void setSell1Volume(BigDecimal sell1Volume) {
		this.sell1Volume = sell1Volume;
	}



	public BigDecimal getBuy1Price() {
		return buy1Price;
	}



	public void setBuy1Price(BigDecimal buy1Price) {
		this.buy1Price = buy1Price;
	}



	public BigDecimal getBuy1Volume() {
		return buy1Volume;
	}



	public void setBuy1Volume(BigDecimal buy1Volume) {
		this.buy1Volume = buy1Volume;
	}



	public BigDecimal getHigh24HPrice() {
		return high24HPrice;
	}



	public void setHigh24HPrice(BigDecimal high24hPrice) {
		high24HPrice = high24hPrice;
	}



	public BigDecimal getLow24HPrice() {
		return low24HPrice;
	}



	public void setLow24HPrice(BigDecimal low24hPrice) {
		low24HPrice = low24hPrice;
	}



	public BigDecimal getAvgPrice() {
		int scale = FcoinUtils.getDecimalScale(coinBase, coinPrice);
		BigDecimal pAvg = sell1Price.add(buy1Price).divide(new BigDecimal(2), scale, BigDecimal.ROUND_HALF_UP);
		return pAvg;
	}

}
