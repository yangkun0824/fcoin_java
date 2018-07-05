package com.yangkun.strategy;

import java.math.BigDecimal;
import java.math.MathContext;

import org.springframework.util.StringUtils;

public class PriceStrategyPercent implements PriceStrategy {

	private BigDecimal priceBase;
	private double percent;
	private int scale;
	
	
	@Override
	public BigDecimal getPrice(String side) {
		// TODO Auto-generated method stub
		
		if( side.equals("buy") ) {
			double pp = 1 - percent;
			return priceBase.multiply(BigDecimal.valueOf(pp), new MathContext(scale));
		} else if( side.equals("sell") ) {
			double pp = 1 + percent;
			return priceBase.multiply(BigDecimal.valueOf(pp), new MathContext(scale));
		}
		
		return priceBase;
	}

	public BigDecimal getPriceBase() {
		return priceBase;
	}

	public void setPriceBase(BigDecimal priceBase) {
		this.priceBase = priceBase;
	}

	public double getPercent() {
		return percent;
	}

	public void setPercent(double percent) {
		this.percent = percent;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

}
