package com.yangkun.fcoin;

public final class FcoinUtils {
	
	public static String getSymbol(String coinBase, String coinPrice) {
		return coinBase + coinPrice;
	}
	
	public static int getPriceDecimalScale(String coinBase, String coinPrice) {
		String symbol = getSymbol(coinBase, coinPrice);
		if( symbol.equals("ftusdt") ||
				symbol.equals("icxeth") ||
				symbol.equals("omgeth") ||
				symbol.equals("aeeth")) {
			return 6;
		} else if( symbol.equals("fteth") || 
				symbol.equals("ftbtc") || 
				symbol.equals("zipeth")) {
			return 8;
		} else if( symbol.equals("btcusdt") || 
				symbol.equals("ethusdt") || 
				symbol.equals("bchusdt") || 
				symbol.equals("ltcusdt") ||
				symbol.equals("etcusdt") ||
				symbol.equals("btcusdt") ) {
			return 2;
		} else if( symbol.equals("btmusdt") || 
				symbol.equals("bnbusdt") ) {
			return 4;
		} 
		return 8;
	}
	
	public static int getAmountDecimalScale(String coinBase, String coinPrice) {
		String symbol = getSymbol(coinBase, coinPrice);
		if( symbol.equals("ftusdt") ) {
			return 2;
		} else if( symbol.equals("fteth") || 
				symbol.equals("ftbtc") || 
				symbol.equals("zipeth")) {
			return 2;
		} else if( symbol.equals("btcusdt") || 
				symbol.equals("ethusdt") || 
				symbol.equals("bchusdt") || 
				symbol.equals("ltcusdt") ||
				symbol.equals("etcusdt") ||
				symbol.equals("btcusdt") ||
				symbol.equals("icxeth") ||
				symbol.equals("omgeth")) {
			return 4;
		} else if( symbol.equals("btmusdt") || 
				symbol.equals("bnbusdt") ||
				symbol.equals("aeeth")) {
			return 2;
		} 
		return 8;
	}
}
