package com.yangkun.strategy;

import java.math.BigDecimal;

public interface PriceStrategy {
	
	//  得到买卖价格
	// buy
	// sell
	public BigDecimal getPrice(String side);

}
