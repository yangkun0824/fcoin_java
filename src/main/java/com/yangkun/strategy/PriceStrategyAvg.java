package com.yangkun.strategy;

import java.math.BigDecimal;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yangkun.model.Candle;
import com.yangkun.model.Ticker;

// 均线策略
public class PriceStrategyAvg implements PriceStrategy {
	private static final Logger logger = LoggerFactory.getLogger(PriceStrategy.class);
	
	private static final int AVG_PRICE_INTERVAL_SHORT = 0;
	private static final int AVG_PRICE_INTERVAL_LONG = 1;
	
	
	
	private String side;
	
	// 短的均线间隔
	private int shortInterval;
	// 长的均线间隔
	private int longInterval;
	// 最近几次下单
	private LinkedList<Ticker> recentTickers;
	// 最近一小时蜡烛图
	private Candle candleH1;
	// 计算percent时，24小时高低价的比重
	private double ratioDay;
	
	

	public PriceStrategyAvg(int shortInterval, int longInterval, LinkedList<Ticker> recentTickers,
			Candle candleH1, double ratioDay) {
		super();
		this.shortInterval = shortInterval;
		this.longInterval = longInterval;
		this.recentTickers = recentTickers;
		this.candleH1 = candleH1;
		this.ratioDay = ratioDay;
	}

	@Override
	public BigDecimal getPrice(String side) {
		// TODO Auto-generated method stub
		
		return null;
	}
	
	private double getIntervalAvg( int intervalType , boolean current) {
		double total=0;
		int start = current ? recentTickers.size() - 1 : recentTickers.size() -2;
		int length = intervalType == AVG_PRICE_INTERVAL_SHORT ? shortInterval : longInterval;
		for( int i=start; i> start-length; i--) {
			Ticker ticker = recentTickers.get(i);
			total += ticker.getMarketPrice().doubleValue();
		}
		return total / length;
	}
	
	// 均线买入or卖出
	// wait: 不操作
	// buy: 市价买入
	// sell: 市价卖出
	public String getSide() {
		if( side == null) {
			if( recentTickers.size() >= longInterval + 1 ) {
				double currentShortAvgPrice = getIntervalAvg(AVG_PRICE_INTERVAL_SHORT, true);
				double lastShortAvgPrice = getIntervalAvg(AVG_PRICE_INTERVAL_SHORT, false);
				
				double currentLongAvgPrice = getIntervalAvg(AVG_PRICE_INTERVAL_LONG, true);
				double lastLongAvgPrice = getIntervalAvg(AVG_PRICE_INTERVAL_LONG, false);
				
				// 短均线价格上穿，买入
				if( currentShortAvgPrice  >  currentLongAvgPrice && lastShortAvgPrice < lastLongAvgPrice ) {
					side = "buy";
				} else if ( currentShortAvgPrice < currentLongAvgPrice  && lastShortAvgPrice > lastLongAvgPrice ) {
					// 价格下穿，卖出
					side = "sell";
				} else {
					side = "wait";
				}
			} else {
				side = "wait";
			}
		}
		
		return side;
	}
	
	private double getOrderPercentWithHighLow(double high, double low) {
		double percent = 0.0;
		Ticker currentTicker = recentTickers.getLast();
		
		if( getSide().equals("buy") ) {
			
			// ( 24小时最高价 - 当前市场价 ) / ( 24小时最高价 - 24小时最低价 )
			percent = ( high - currentTicker.getMarketPrice().doubleValue() ) / ( high - low );
			
		} else if( getSide().equals("sell") ) {
			
			// ( 当前市场价 - 24小时最低价 ) / ( 24小时最高价 - 24小时最低价 )
			percent = ( currentTicker.getMarketPrice().doubleValue() - low ) / ( high - low );
		}
		
		return percent;
	}

	// 根据24小时最高价和最低价，结合当前买卖和最新价格，生成下单的概率
	public double getOrderPercent() {
		double percent = 0.0;
		Ticker currentTicker = recentTickers.getLast();
		
		double dayPercent = getOrderPercentWithHighLow(currentTicker.getHigh24HPrice().doubleValue(), currentTicker.getLow24HPrice().doubleValue());
		double hourPercent = getOrderPercentWithHighLow(candleH1.getHigh().doubleValue(), candleH1.getLow().doubleValue());
		
		percent = dayPercent * ratioDay + hourPercent * ( 1- ratioDay );
		
		logger.info("{}单 日概率:{} 小时概率:{} 日比重:{} 最终概率:{}", getSide(), dayPercent, hourPercent, ratioDay, percent);
		
		return percent;
	}
	

	public LinkedList<Ticker> getRecentTickers() {
		return recentTickers;
	}

	public void setRecentTickers(LinkedList<Ticker> recentTickers) {
		this.recentTickers = recentTickers;
	}
}
