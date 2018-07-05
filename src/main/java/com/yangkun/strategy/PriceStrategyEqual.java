package com.yangkun.strategy;

import java.math.BigDecimal;

public class PriceStrategyEqual implements PriceStrategy {
	
	private BigDecimal sell1Price;
	private BigDecimal buy1Price;
	private int scale;

	@Override
	public BigDecimal getPrice(String side) {
		// TODO Auto-generated method stub
		return sell1Price.add(buy1Price).divide(new BigDecimal(2), scale, BigDecimal.ROUND_HALF_UP);
	}

	public BigDecimal getSell1Price() {
		return sell1Price;
	}

	public void setSell1Price(BigDecimal sell1Price) {
		this.sell1Price = sell1Price;
	}

	public BigDecimal getBuy1Price() {
		return buy1Price;
	}

	public void setBuy1Price(BigDecimal buy1Price) {
		this.buy1Price = buy1Price;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

}
