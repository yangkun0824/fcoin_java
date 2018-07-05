package com.yangkun.strategy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;

// 交易策略参考
// https://www.botvs.com/strategy/1088
public class PriceStrategyDepth implements PriceStrategy {

	// 最小价差
	private double priceDiff;
	
	// 上下寻找的交易个数
	private double amount;
	
	// 买单深度队列 [价1,量1,价2,量2]
	private ArrayList<BigDecimal> bids;
	
	// 卖单深度队列
	private ArrayList<BigDecimal> asks;
	
	private int scale;
	
	@Override
	public BigDecimal getPrice(String side) {
		// TODO Auto-generated method stub
		double totalBuy = amount;
		double totalSell = amount;
		double buyPrice = bids.get(0).doubleValue();
		double sellPrice = asks.get(0).doubleValue();
		
		for( int i=0; i<bids.size(); i++) {
			double price = bids.get(i*2).doubleValue();
			double volume =  bids.get(i*2+1).doubleValue();
			
			if( volume >= totalBuy ) {
				buyPrice = price + (1 / Math.pow(10, scale));
				break;
			} else {
				buyPrice = price;
				totalBuy -= volume;
			}
		}
		
		for( int i=0; i<asks.size(); i++) {
			double price = asks.get(i*2).doubleValue();
			double volume =  asks.get(i*2+1).doubleValue();
			
			if( volume >= totalSell ) {
				sellPrice = price - (1 / Math.pow(10, scale));
				break;
			} else {
				sellPrice = price;
				totalSell -= volume;
			}
		}
		
		if( buyPrice <= sellPrice ) {
			if( side.equals("buy") ) {
				return new BigDecimal(buyPrice, new MathContext(scale));
			} else {
				return new BigDecimal(sellPrice, new MathContext(scale));
			}
		} else {
			BigDecimal sell1Price = bids.get(0);
			BigDecimal buy1Price = asks.get(0);
			
			return sell1Price.add(buy1Price).divide(new BigDecimal(2), scale, BigDecimal.ROUND_HALF_UP);
		}
	}

	public double getPriceDiff() {
		return priceDiff;
	}

	public void setPriceDiff(double priceDiff) {
		this.priceDiff = priceDiff;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public ArrayList<BigDecimal> getBids() {
		return bids;
	}

	public void setBids(ArrayList<BigDecimal> bids) {
		this.bids = bids;
	}

	public ArrayList<BigDecimal> getAsks() {
		return asks;
	}

	public void setAsks(ArrayList<BigDecimal> asks) {
		this.asks = asks;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

}
