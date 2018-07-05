package com.yangkun.model;

import java.math.BigDecimal;
import java.util.ArrayList;

import com.yangkun.fcoin.FcoinUtils;

public class Depth {
	
	// 基准货币
	private String coinBase;
	// 定价货币
	private String coinPrice;
	// 买队列
	private ArrayList<BigDecimal> bids;
	// 卖队列
	private ArrayList<BigDecimal> asks;
	
	public Depth(String coinBase, String coinPrice, ArrayList<BigDecimal> bids, ArrayList<BigDecimal> asks) {
		super();
		this.coinBase = coinBase;
		this.coinPrice = coinPrice;
		this.bids = bids;
		this.asks = asks;
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
	
	public BigDecimal getSell1Price() {
		return asks.get(0);
		
	}
	
	public BigDecimal getBuy1Price() {
		return bids.get(0);
	}
	
	public BigDecimal getAvgPrice() {
		int scale = FcoinUtils.getDecimalScale(coinBase, coinPrice);
		return getSell1Price().add(getBuy1Price()).divide(new BigDecimal(2), scale, BigDecimal.ROUND_HALF_UP);
	}
}
