package com.yangkun.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class Order implements Serializable {

	private String orderId;
	private String coinBase;
	private String coinPrice;
	private String symbol;
	private String type;				// limit or market
	private String side;				// buy or sell
	private BigDecimal price;		// 限价单金额
	private BigDecimal amount;
	
	// 状态
	//	submitted	已提交
	//	partial_filled	部分成交
	//	partial_canceled	部分成交已撤销
	//	filled	完全成交
	//	canceled	已撤销
	//	pending_cancel	撤销已提交
	private String state;
	
	// 比如：如果是ft/usdt交易对，
	// 买入: 显示真实买入的usdt个数
	// 卖出: 显示usdt到账个数
	private BigDecimal executedValue;		
	
	// 交易手续费
	private BigDecimal fillFees;
	
	// 成交个数
	private BigDecimal filledAmount;
	
	private BigDecimal createdAt;
	private String source;
	
	public Order(String orderId, String coinBase, String coinPrice, String symbol, String type, String side,
			BigDecimal price, BigDecimal amount, String state, BigDecimal executedValue, BigDecimal fillFees,
			BigDecimal filledAmount, BigDecimal createdAt, String source) {
		super();
		this.orderId = orderId;
		this.coinBase = coinBase;
		this.coinPrice = coinPrice;
		this.symbol = symbol;
		this.type = type;
		this.side = side;
		this.price = price;
		this.amount = amount;
		this.state = state;
		this.executedValue = executedValue;
		this.fillFees = fillFees;
		this.filledAmount = filledAmount;
		this.createdAt = createdAt;
		this.source = source;
	}

	
	@Override
	public String toString() {
		return "Order [orderId=" + orderId + ", coinBase=" + coinBase + ", coinPrice=" + coinPrice + ", symbol="
				+ symbol + ", type=" + type + ", side=" + side + ", price=" + price + ", amount=" + amount + ", state="
				+ state + ", executedValue=" + executedValue + ", fillFees=" + fillFees + ", filledAmount="
				+ filledAmount + ", createdAt=" + createdAt + ", source=" + source + "]";
	}

	// 得到真实基准货币交易个数
	// 比如ft/usdt交易对，usdt买入
	// 手续费为ft的个数
	// executePrice为真实花费usdt的个数
	//
	// 比如ft/usdt交易对，ft卖出
	// 手续费为usdt的个数
	// filledAmount为真实花费ft的个数
	public double getBaseCoinNum() {
		if( side.equals("buy") ) {
			return fillFees.doubleValue() * 1000;
		} else if( side.equals("sell") ) {
			return filledAmount.doubleValue();
		}
		return 0.0;
	}
	
	// 得到定价货币交易个数
	public double getPriceCoinNum() {
		if( side.equals("buy") ) {
			return executedValue.doubleValue();
		} else if( side.equals("sell") ) {
			return fillFees.doubleValue() * 1000;
		}
		return 0.0;
	}

	// 得到成交平均价格
	public double getFillAvgPrice() {
		double baseCoinNum = getBaseCoinNum();
		double priceCoinNum = getPriceCoinNum();
		
		if( priceCoinNum != 0 ) {
			return priceCoinNum / baseCoinNum;
		}
		return 0;
	}


	public String getOrderId() {
		return orderId;
	}


	public void setOrderId(String orderId) {
		this.orderId = orderId;
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


	public String getSymbol() {
		return symbol;
	}


	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}


	public String getType() {
		return type;
	}


	public void setType(String type) {
		this.type = type;
	}


	public String getSide() {
		return side;
	}


	public void setSide(String side) {
		this.side = side;
	}


	public BigDecimal getPrice() {
		return price;
	}


	public void setPrice(BigDecimal price) {
		this.price = price;
	}


	public BigDecimal getAmount() {
		return amount;
	}


	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}


	public String getState() {
		return state;
	}


	public void setState(String state) {
		this.state = state;
	}


	public BigDecimal getExecutedValue() {
		return executedValue;
	}


	public void setExecutedValue(BigDecimal executedValue) {
		this.executedValue = executedValue;
	}


	public BigDecimal getFillFees() {
		return fillFees;
	}


	public void setFillFees(BigDecimal fillFees) {
		this.fillFees = fillFees;
	}


	public BigDecimal getFilledAmount() {
		return filledAmount;
	}


	public void setFilledAmount(BigDecimal filledAmount) {
		this.filledAmount = filledAmount;
	}


	public BigDecimal getCreatedAt() {
		return createdAt;
	}


	public void setCreatedAt(BigDecimal createdAt) {
		this.createdAt = createdAt;
	}


	public String getSource() {
		return source;
	}


	public void setSource(String source) {
		this.source = source;
	}

	
	
	
}
