package com.yangkun.fcoin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.objenesis.strategy.SerializingInstantiatorStrategy;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.yangkun.model.Ticker;
import com.yangkun.model.Balance;
import com.yangkun.model.Candle;
import com.yangkun.model.Depth;
import com.yangkun.model.Order;
import com.yangkun.strategy.PriceStrategy;
import com.yangkun.strategy.PriceStrategyAvg;
import com.yangkun.strategy.PriceStrategyDepth;
import com.yangkun.strategy.PriceStrategyEqual;
import com.yangkun.strategy.PriceStrategyPercent;

import afu.org.checkerframework.checker.units.qual.degrees;
import afu.org.checkerframework.common.reflection.qual.NewInstance;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Hello world!
 *
 */
public class App 
{
	private static final Logger logger = LoggerFactory.getLogger(App.class);
	
	private static final int ORDER_STRATEGY_EQUALITY = 0;		// 买卖价相同
	private static final int ORDER_STRATEGY_PERCENT = 1;		// 卖价高，买价低
	private static final int ORDER_STRATEGY_DEPTH = 2;		// 买卖深度定价
	private static final int ORDER_STRATEGY_AVG = 3;			// 均线策略
	
	private static final String baseUrl = "https://api.fcoin.com/v2/";
	private static final MediaType MEDIATYPE_JSON = MediaType.parse("application/json; charset=utf-8");
	
	private static String key;
	private static String secret;
	private static String coinBase;
	private static String coinPrice;
	private static String coinMin;			// 基础货币最小余额，小于这个余额就暂停10秒
	private static boolean bonusSell;			// 是否选择在分红后迅速卖出ft
	private static double bonusPercent;		// ft仓位的变化的比率
	
	private static Ticker currentTicker = null;
	private static Depth currentDepth = null;
	private static Candle currentCandleH1 = null;
	
	private static int orderStrategy = 0;
	
	private static String orderAmount;		// 每次下单amount
	private static int orderCount;		// 下单次数
	
	private static int avgShortInterval;		// 均线短间隔秒数
	private static int avgLongInterval;		// 均线长间隔秒数
	private static double avgRevenue;	// 均线下单利润率
	private static double avgRatioDay;	// 均线下单日比重
	private static LinkedList<Ticker> avgRecentTickers = new LinkedList<>();
	private static HashMap<String, Order> avgBuyOrders = new HashMap<>(); 		// 均线做多队列
	private static HashMap<String, Order> avgSellOrders = new HashMap<>(); 		// 均线做空队列
	private static HashMap<String, Order> avgSubmittedOrders = new HashMap<>();   // 均线成交队列
	private static HashMap<String, Boolean> avgSubmittedOrderIds = new HashMap<>();
	private static Object avgOrdersLock = new Object();
	
	
	private static OkHttpClient client = new OkHttpClient.Builder().build();
	
	private static final HashMap<String, Balance> balance = new HashMap<String, Balance>();
	
	// 交易retryer
	private static Retryer<String> tradeRetryer = RetryerBuilder.<String>newBuilder()
			.retryIfResult( r -> r == null )
	        .retryIfException()
	        .withStopStrategy(StopStrategies.stopAfterAttempt(5))
	        .withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
	        .withRetryListener(new RetryListener() {
	            public <V> void onRetry(Attempt<V> attempt) {
	                if (attempt.hasException()){
	                    logger.error("trade retry exception {}", attempt.getAttemptNumber());
	                    attempt.getExceptionCause().printStackTrace();
	                }
	                
	                if( attempt.hasResult() ) {
	                		logger.info("trade retry result: {} number: {}", attempt.getResult(), attempt.getAttemptNumber());
	                }
	            }
	        })
	        .build();
	
	// 普通retryer
	private static Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
			.retryIfResult( r -> r.equals(false))
	        .retryIfException()
	        .withStopStrategy(StopStrategies.stopAfterAttempt(10))
	        .withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
	        .withRetryListener(new RetryListener() {
	            public <V> void onRetry(Attempt<V> attempt) {
	                if (attempt.hasException()){
	                    logger.error("retry exception {}", attempt.getAttemptNumber());
	                    attempt.getExceptionCause().printStackTrace();
	                }
	                
	                if( attempt.hasResult() ) {
	                		logger.info("retry result: {} number: {}", attempt.getResult(), attempt.getAttemptNumber());
	                }
	            }
	        })
	        .build();
	
	private static Retryer<Object> objRetryer = RetryerBuilder.<Object>newBuilder()
			.retryIfResult( r -> r == null )
	        .retryIfException()
	        .withStopStrategy(StopStrategies.stopAfterAttempt(10))
	        .withWaitStrategy(WaitStrategies.fixedWait(1000, TimeUnit.MILLISECONDS))
	        .withRetryListener(new RetryListener() {
	            public <V> void onRetry(Attempt<V> attempt) {
	                if (attempt.hasException()){
	                    logger.error("retry exception {}", attempt.getAttemptNumber());
	                    attempt.getExceptionCause().printStackTrace();
	                }
	                
	                if( attempt.hasResult() ) {
	                		logger.info("retry result: {} number: {}", attempt.getResult(), attempt.getAttemptNumber());
	                }
	            }
	        })
	        .build();
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		try {
			
			// 读取配置
			if( !getProperties() ) {
				return ;
			}
			
			if( orderStrategy == ORDER_STRATEGY_AVG ) {
				getAvgOrdersFromFile();
				
				logger.info("买多队列个数:{}", avgBuyOrders.size());
				logger.info("卖空队列个数:{}", avgSellOrders.size());
				logger.info("总提交订单个数:{}", avgSubmittedOrders.size());
				
				// 启动线程刷新order信息
				startCheckSubmittedOrders();
			}
			
			// 开始挖矿
			mining(coinBase, coinPrice, orderAmount, orderCount);
			
			// 蜡烛图
//			Candle candle = retryCandle(Candle.CANDLE_RESOLUTION_H1, 1).get(0);
//			logger.info(candle.toString());
			
			// 买单
//			Order order = retryOrder("AhYLeg_VY05avk3HSvgx9ALG12yaFQLA4W17N3jXF84=");
//			Order order = retryOrder("Y2Qme5gLKABbG2tZ6p2Q_qwqpekn3KryCfgBR6RaPVY=");
//			logger.info(order.toString());
//			logger.info("{}", order.getFillAvgPrice());
			
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
	}
	
	private static void startCheckSubmittedOrders() {
		new Thread() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				while(true) {
					logger.info("开始检测市价订单状态");
					try {
						synchronized (avgOrdersLock) {
							if( avgSubmittedOrderIds.size() > 0 ) {

								for (Iterator<Map.Entry<String, Boolean>> it = avgSubmittedOrderIds.entrySet().iterator(); it.hasNext();){
								    Map.Entry<String, Boolean> item = it.next();
									String orderId = item.getKey();
									boolean add = item.getValue().booleanValue();
									
									Order order = retryOrder(orderId);
									
									if( order.getState().equals("filled")) {
										logger.info("市价单:{} 已完全成交", order.getSide());
										logger.info("{}成交个数:{} {}成交个数:{} 成交均价:{}", coinBase, order.getBaseCoinNum(), coinPrice, order.getPriceCoinNum(), order.getFillAvgPrice());
										
										if( add ) {
											if( order.getSide().equals("buy") ) {
												avgBuyOrders.put(orderId, order);
											} else {
												avgSellOrders.put(orderId, order);
											}
										}
										avgSubmittedOrders.put(orderId, order);
										saveAvgOrdersToFile();
										
										// 删除
										it.remove();
									}
								}
							}
						}
						
						logger.info("市价订单状态检查完毕，暂停5秒");
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}.start();
	}
	
	private static String base64String(String str) throws UnsupportedEncodingException {
		return Base64.getEncoder().encodeToString(str.getBytes("UTF-8"));
	}
	
	private static String hmacSha1AndBase64(String KEY, String VALUE) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(KEY.getBytes("UTF-8"), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(VALUE.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(rawHmac);

//            byte[] hexArray = {
//                    (byte)'0', (byte)'1', (byte)'2', (byte)'3',
//                    (byte)'4', (byte)'5', (byte)'6', (byte)'7',
//                    (byte)'8', (byte)'9', (byte)'a', (byte)'b',
//                    (byte)'c', (byte)'d', (byte)'e', (byte)'f'
//            };
//            byte[] hexChars = new byte[rawHmac.length * 2];
//            for ( int j = 0; j < rawHmac.length; j++ ) {
//                int v = rawHmac[j] & 0xFF;
//                hexChars[j * 2] = hexArray[v >>> 4];
//                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
//            }
//            return new String(hexChars);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
	
	private static String getParamsString(Map<String, String> params) throws UnsupportedEncodingException{
		// key排序
		Map<String, String> sortedMap = new TreeMap<String, String>(
                new Comparator<String>() {
                    public int compare(String obj1, String obj2) {
                        // 升序排序
                        return obj1.compareTo(obj2);
                    }
                });
		sortedMap.putAll(params);

        StringBuilder result = new StringBuilder();
 
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
          result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
          result.append("=");
          result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
          result.append("&");
        }
 
        String resultString = result.toString();
        return resultString.length() > 0
          ? resultString.substring(0, resultString.length() - 1)
          : resultString;
    }
	
	private static String getSignString(String method, String requestUrl, long currentTime, Map<String, String> parameters ) throws UnsupportedEncodingException {
		logger.debug("系统当前时间:{}", currentTime);
		
		// 数据加密
		StringBuffer encryptStr = new StringBuffer();
		encryptStr.append(method);
		encryptStr.append(baseUrl);
		encryptStr.append(requestUrl);
		encryptStr.append(currentTime);
		if( parameters != null && parameters.size() > 0 ) {
			encryptStr.append(getParamsString(parameters));
		}
		
		logger.debug("加密前数据:{}", encryptStr.toString());
		
		// 第一次base64加密
		String base64First = base64String(encryptStr.toString());
		logger.debug("第一次base64加密后数据:{}", base64First);
		
		// hmacsha1加密后base64加密
		String hmacsha1Str = hmacSha1AndBase64(secret, base64First);
		logger.debug("hamcsha1加密:{}", hmacsha1Str);
			
		return hmacsha1Str;
	}
	
	private static void printBalance() {
		logger.info(balance.get(coinBase).toString());
		logger.info(balance.get(coinPrice).toString());
	}
	
	private static BigDecimal getPrice(String coinBase, String coinPrice , String side) {
		PriceStrategy st = null;
		
		String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
		if( orderStrategy == ORDER_STRATEGY_EQUALITY ) {
			PriceStrategyEqual pse = new PriceStrategyEqual();
			pse.setSell1Price(currentTicker.getSell1Price());
			pse.setBuy1Price(currentTicker.getBuy1Price());
			pse.setScale(FcoinUtils.getPriceDecimalScale(coinBase, coinPrice));
			
			st = pse;
		} else if( orderStrategy == ORDER_STRATEGY_PERCENT ) {
			PriceStrategyPercent psp = new PriceStrategyPercent();
			psp.setPercent(0.001);
			psp.setPriceBase(currentTicker.getMarketPrice());
			psp.setScale(FcoinUtils.getPriceDecimalScale(coinBase, coinPrice));
			
			st = psp;
		} else {
			PriceStrategyDepth psd = new PriceStrategyDepth();
			psd.setAmount(Double.parseDouble(orderAmount));
			psd.setPriceDiff(1.0);
			psd.setScale(FcoinUtils.getPriceDecimalScale(coinBase, coinPrice));
			psd.setAsks(currentDepth.getAsks());
			psd.setBids(currentDepth.getBids());
			
			st = psd;	
		}
		
		if( st != null ) {
			return st.getPrice(side);
		} else {
			return currentTicker.getMarketPrice();
		}
	} 
	
	private static boolean getProperties() throws Exception {
		
		// 外部版本
//		String configPath = "./config.properties";  
// 
//        BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(configPath));  
//        ResourceBundle rBundle = new PropertyResourceBundle(inputStream);  
//        inputStream.close();  
		
		// 内部版本
		ResourceBundle rBundle = ResourceBundle.getBundle("config");
        
		key = rBundle.getString("fcoin.key");
	    secret = rBundle.getString("fcoin.secret");
		
		if( key == null || key.isEmpty() ) {
			logger.error("fcoin key为空");
			return false;
		}
		
		if( secret == null || secret.isEmpty() ) {
			logger.error("fcoin secret为空");
			return false;
		}
		
		logger.info("key:{}", key);
		logger.info("secret:{}", secret);
		
		coinBase = rBundle.getString("coin.base");
		coinPrice = rBundle.getString("coin.price");
		coinMin = rBundle.getString("coin.min");
		
		if( coinBase == null || coinBase.isEmpty() ) {
			logger.error("基准货币coin.base为空");
			return false;
		}
		
		if( coinPrice == null || coinPrice.isEmpty() ) {
			logger.error("定价货币coin.price为空");
			return false;
		}
		
		if( coinMin == null || coinMin.isEmpty() ) {
			logger.error("最小余额coin.min为空");
			return false;
		}
		
		logger.info("基准货币:{}", coinBase);
		logger.info("定价货币:{}", coinPrice);
		logger.info("当前交易对:{}{}", coinBase, coinPrice);
		logger.info("最小余额:{}", coinMin);
		
		orderAmount = rBundle.getString("order.amount");
		
		if( Double.parseDouble(orderAmount) <= 0 ) {
			logger.error("每次交易个数需大于0");
			return false;
		}
		
		logger.info("每次交易基准货币个数:{}", orderAmount);
		
		orderCount = Integer.parseInt(rBundle.getString("order.count"));
		
		if( orderCount <= 0 ) {
			logger.error("脚本交易次数需大于0");
			return false;
		}
		logger.info("脚本提交订单交易次数:{}次", orderCount);
		
		int orderExpr = Integer.parseInt(rBundle.getString("order.expiration"));
		
		if( orderExpr <= 0 ) {
			orderExpr = 0;
		}
		logger.info("订单未成交过期时间:{}秒", orderExpr);
		
		orderStrategy = Integer.parseInt(rBundle.getString("order.strategy"));
		logger.info("当前下单策略:{}", orderStrategy);
		
		bonusSell = Boolean.parseBoolean(rBundle.getString("bonus.sell"));
		bonusPercent = Double.parseDouble(rBundle.getString("bonus.percent"));
		logger.info("获得ft奖励是否卖出:{}", bonusSell);
		logger.info("奖励Percent:{}", bonusPercent);
		
		avgShortInterval = Integer.parseInt(rBundle.getString("avg.short"));
		avgLongInterval = Integer.parseInt(rBundle.getString("avg.long"));
		avgRevenue = Double.parseDouble(rBundle.getString("avg.revenue"));
		avgRatioDay = Double.parseDouble(rBundle.getString("avg.ratioday"));
		logger.info("均线策略:{}秒短均线 {}秒长均线", avgShortInterval, avgLongInterval);
		logger.info("均线利润率:{}", avgRevenue);
		logger.info("均线下单概率日比重:{}", avgRatioDay);
		
		return true;
	}
	
	private static void saveAvgOrdersToFile() {
		ObjectOutputStream os;
		try {
			os = new ObjectOutputStream(new FileOutputStream("avg_orders.bak"));
			os.writeObject(avgBuyOrders);
			os.writeObject(avgSellOrders);
			os.writeObject(avgSubmittedOrders);
	        os.close();  
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void getAvgOrdersFromFile() {
		ObjectInputStream is;
		try {
			File f = new File("avg_orders.bak");
			if(f.exists() && !f.isDirectory()) { 
				is = new ObjectInputStream(new FileInputStream(f));
				avgBuyOrders = (HashMap<String, Order>) is.readObject();
				avgSellOrders = (HashMap<String, Order>) is.readObject();
				avgSubmittedOrders = (HashMap<String, Order>) is.readObject();
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  
        
	}
	
	public static void mining(String coinBase, String coinPrice, String amount, int tradeCount) throws Exception {
		logger.info("============================开始{}{}交易============================", coinBase, coinPrice);
		
		String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
		
		// 计算最小持仓
		retryTicker(coinBase, coinPrice);
		retryDepth(coinBase, coinPrice);
		double lastPrice = currentTicker.getMarketPrice().doubleValue();
		double coinBaseMin = Double.parseDouble(coinMin);
		double coinPriceMin = coinBaseMin  * lastPrice;
		
		for( int i=1; i <= tradeCount; i++ ) {
			logger.info("============================第{}次交易============================", i);
			
			
//			logger.info("=======>步骤1: 检测是否有FT分红");
//			
//			if( i==1 ) {
//				retryBalance();
//				logger.info("第一次交易不检测是否有FT分红");
//			} else {
//				double lastFtBalance = balance.get("ft").getBalance();
//				retryBalance();
//				double currentFtBalance = balance.get("ft").getBalance();
//				if( (currentFtBalance - lastFtBalance) / lastFtBalance > bonusPercent   ) {
//					logger.info("检测有分红");
//					// TODO 发送短信
//					if( bonusSell ) {
//						logger.info("拿到分红，卖出FT市价单");
//						// TODO 生成市价单
//						retrySellMarketOrder("ft", "usdt", String.valueOf(lastFtBalance - currentFtBalance));
//						
//						logger.error("观望币价, 暂停60秒");
//						Thread.sleep(600000);
//						continue;
//					}
//				} else {
//					logger.info("检测无分红");
//				}
//			}
			
			// 获取当前基准货币和定价货币余额
			logger.info("=======>步骤1: 检测余额是否足够");
			
			retryBalance();
			
			double coinBaseAvailable = balance.get(coinBase).getAvailable();
			double coinPriceAvailable = balance.get(coinPrice).getAvailable();
			
			logger.info("基础货币:{} 当前可用:{} 最小可用:{}", coinBase, coinBaseAvailable, coinBaseMin);
			logger.info("定价货币:{} 当前可用:{} 最小可用:{}", coinPrice, coinPriceAvailable, coinPriceMin);
			
			// 均线策略
			if( orderStrategy == ORDER_STRATEGY_AVG ) {
				logger.info("=======>步骤2: 开始均线策略买卖");
				
				currentCandleH1 = retryCandle(Candle.CANDLE_RESOLUTION_H1, 1).get(0);
				retryTicker(coinBase, coinPrice);
				
				if(avgRecentTickers.size() > avgLongInterval * 2 ) {
					avgRecentTickers.removeFirst();
				}
				avgRecentTickers.add(currentTicker);
				
				// 创建均线策略
				PriceStrategyAvg priceStrategyAvg = new PriceStrategyAvg(avgShortInterval, avgLongInterval, avgRecentTickers, currentCandleH1, avgRatioDay);
				String resultStrategy = priceStrategyAvg.getSide();
				
				logger.info("=======>步骤3: 查看均线策略显示结果");
				
				if( resultStrategy.equals("wait") ) {
					logger.info("均线结果显示等待，暂停1秒");
				} else if( resultStrategy.equals("buy") ) {
					logger.info("均线结果显示买入");
					
					// 有对应卖出的队列
					boolean hasBuy = false;
					double amountTotal = 0.0;
					
					synchronized (avgOrdersLock) {
						if( avgSellOrders.size() > 0 ) {
							logger.info("查看卖空队列,检测是否可获利买入,共{}个卖空订单", avgSellOrders.size());
							
							for (Iterator<Map.Entry<String, Order>> it = avgSellOrders.entrySet().iterator(); it.hasNext();){
							    Map.Entry<String, Order> item = it.next();
							    
							    // 计算是否有获利，如果有获利,则买入
							    double currentMarketPrice = currentTicker.getMarketPrice().doubleValue();
							    // 得到订单平均成交价格
							    double orderPrice = item.getValue().getFillAvgPrice();
							    
							    double revenue = (orderPrice - currentMarketPrice) / orderPrice;
							    logger.info("当前市场价格:{}, 卖单成交均价:{}, 基准货币个数:{} 定价货币个数:{} 利润率:{}", currentMarketPrice, orderPrice, item.getValue().getBaseCoinNum(), item.getValue().getPriceCoinNum(), revenue);
							    logger.debug(item.getValue().toString());
							    logger.debug("{}成交个数:{} {}成交个数:{} 成交均价:{}", coinBase, item.getValue().getBaseCoinNum(), coinPrice, item.getValue().getPriceCoinNum(), item.getValue().getFillAvgPrice());
							    
							    // 1个点利润
							    if( currentMarketPrice <  orderPrice && (orderPrice - currentMarketPrice) / orderPrice > avgRevenue ) {
							    		
							    		if( amountTotal + item.getValue().getPriceCoinNum() > coinPriceAvailable ) {
							    			// 余额不足的情况
							    			logger.info("定价货币个数不足，跳出卖空队列检测");
							    			break;
							    		} else {
							    			// 定价货币个数
								    		amountTotal += item.getValue().getPriceCoinNum();
								    		
								    		it.remove();
							    		}
							    }
							}
							
							// 市价买入
							if( amountTotal > 0) {
								hasBuy = true;
								logger.info("卖空队列可获利买入");
								
							}
						}
					}
					
					double am = amountTotal;
					if( !hasBuy ) {
						// 不可获利买入
						logger.info("卖空队列不可获利买入, 计算买入概率");
						double percent = priceStrategyAvg.getOrderPercent();
						double rand = Math.random();
						logger.info("买入概率:{} 随机概率:{}", percent, rand);
						
						// 概率范围内
						if( rand <= percent ) {
							logger.info("在概率范围，买入");
							//市价购买，此处是定价货币的个数
							double coinPriceAmount = Double.valueOf(orderAmount).doubleValue();
							am =  coinPriceAmount * currentTicker.getMarketPrice().doubleValue();
						} else {
							logger.info("不在概率范围，不买入，暂停1秒");
							Thread.sleep(1000);
							continue;
						}
					}
					
					// 余额不足
					if( am > coinPriceAvailable ) {
						logger.info("余额不足 下单个数:{} 当前可用余额:{} 暂停1秒", am, coinPriceAvailable);
						Thread.sleep(1000);
						continue;
					}
					
					// 市价买入
					BigDecimal amBigDecimal = new BigDecimal(am);
					double am1 = amBigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					String orderId = retryBuyMarketOrder(coinBase, coinPrice, String.valueOf(am1));
					
					if( orderId != null ) {
						
						synchronized (avgOrdersLock) {
							avgSubmittedOrderIds.put(orderId, Boolean.valueOf(!hasBuy));
						}
						
						if( !hasBuy ) {
							logger.info("添加到买入队列");
						} else {
							logger.info("有对应卖出队列，获利买入");
						}
					}
					
				} else if( resultStrategy.equals("sell") ) {
					logger.info("均线结果显示卖出");
					
					// 有对应卖出的队列
					boolean hasSell = false;
					double amountTotal = 0.0;
					
					synchronized (avgOrdersLock) {
						if( avgBuyOrders.size() > 0 ) {
							logger.info("查看买多队列,检测是否可获利卖出,共{}个买多订单", avgBuyOrders.size());
							
							for (Iterator<Map.Entry<String, Order>> it = avgBuyOrders.entrySet().iterator(); it.hasNext();){
							    Map.Entry<String, Order> item = it.next();
							    
							    // 计算是否有获利，如果有获利，则卖出
							    double currentMarketPrice = currentTicker.getMarketPrice().doubleValue();
							    double orderPrice = item.getValue().getFillAvgPrice();
							    
							    double revenue = (currentMarketPrice - orderPrice) / orderPrice;
							    logger.info("当前市场价格:{}, 买单成交均价:{}, 基准货币个数:{} 定价货币个数:{} 利润率:{}", currentMarketPrice, orderPrice, item.getValue().getBaseCoinNum(), item.getValue().getPriceCoinNum(), revenue);
							    logger.debug(item.getValue().toString());
							    logger.debug("{}成交个数:{} {}成交个数:{} 成交均价:{}", coinBase, item.getValue().getBaseCoinNum(), coinPrice, item.getValue().getPriceCoinNum(), item.getValue().getFillAvgPrice());
							       
							    // 1个点利润卖出
							    if( currentMarketPrice >  orderPrice && (currentMarketPrice - orderPrice) / orderPrice > avgRevenue ) {
							    		
								    	if( amountTotal + item.getValue().getBaseCoinNum() > coinBaseAvailable ) {
								    		// 余额不足的情况
							    			logger.info("基准货币个数不足，跳出买多队列检测");
							    			break;
								    	} else {
								    		amountTotal += item.getValue().getBaseCoinNum();
								    		
								    		it.remove();
								    	}
							    }
							}
							
							// 市价卖出
							if( amountTotal > 0) {
								hasSell = true;
								logger.info("买多队列可获利卖出");
							}
						}
					}
					
					double am = amountTotal;
					if( !hasSell ) {
						// 不可获利卖出
						logger.info("买多队列不可获利卖出, 计算卖出概率");
						double percent = priceStrategyAvg.getOrderPercent();
						double rand = Math.random();
						logger.info("卖出概率:{} 随机概率:{}", percent, rand);
						
						if( rand <= percent ) {
							logger.info("在概率范围，卖出");
							am = Double.parseDouble(orderAmount);
						} else {
							logger.info("不在概率范围，不卖出，暂停1秒");
							Thread.sleep(1000);
							continue;
						}
					} 
					
					// 余额不足
					if( am > coinBaseAvailable ) {
						logger.info("余额不足 下单个数:{} 当前可用余额:{} 暂停1秒", am, coinBaseAvailable);
						Thread.sleep(1000);
						continue;
					}
					
					// 市价卖出
					BigDecimal amBigDecimal = new BigDecimal(am);
					double am1 = amBigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					String orderId = retrySellMarketOrder(coinBase, coinPrice, String.valueOf(am1));
					
					if( orderId != null ) {
						
						synchronized (avgOrdersLock) {
							avgSubmittedOrderIds.put(orderId, Boolean.valueOf(!hasSell));
						}
						
						if( !hasSell ) {
							logger.info("添加到卖出队列");
						} else {
							logger.info("有对应买入队列，获利卖出");
						}
					}
				}
				
				Thread.sleep(2000);
				
			} else {
				
				if( coinBaseAvailable <= coinBaseMin || coinPriceAvailable <= coinPriceMin ) {
					logger.error("余额不足, 暂停10秒");
					Thread.sleep(10000);
					continue;
				}  else {
					logger.info("余额足够开始交易");
				}
				
				logger.info("=======>步骤2: 获取价格ticker或买卖Depth信息");
				
				// 非均线策略
				BigDecimal lastPriceOrder = null;
				BigDecimal currentPriceOrder = null;
				// 读取交易所当前最大买一，最小卖一价格，生成限价卖单价
				// 如果是深度策略挂单，则先取深度信息
				if( orderStrategy == ORDER_STRATEGY_EQUALITY || orderStrategy == ORDER_STRATEGY_PERCENT ) {
					lastPriceOrder = currentTicker.getAvgPrice();
					retryTicker(coinBase, coinPrice);
					currentPriceOrder = currentTicker.getAvgPrice();
					
					logger.info("当前卖一价:{}", currentTicker.getSell1Price());
					logger.info("当前买一价:{}", currentTicker.getBuy1Price());
					logger.info("卖一买一中间价:{}", currentTicker.getAvgPrice());
					
				} else {
					lastPriceOrder = currentDepth.getAvgPrice();
					retryDepth(coinBase, coinPrice);
					currentPriceOrder = currentDepth.getAvgPrice();
					
					logger.info("当前卖一价:{}", currentDepth.getSell1Price());
					logger.info("当前买一价:{}", currentDepth.getBuy1Price());
					logger.info("卖一买一中间价:{}", currentDepth.getAvgPrice());
				}
				
				logger.info("=======>步骤3: 比较价格");
				
				if( Math.abs(currentPriceOrder.doubleValue() - lastPriceOrder.doubleValue()) / lastPriceOrder.doubleValue() > 0.001 ) {
					logger.info("价格变动较大，跳过");
					continue;
				}
				
				// 价格上涨，先提交买单
				if( currentPriceOrder.compareTo(lastPriceOrder) >= 0) {
					logger.info("价格上涨，先买后卖 current:{} last:{}", currentPriceOrder, lastPriceOrder);
					logger.info("=======>步骤4: 提交买单");
					// 提交买单
					retryBuyLimitOrder(coinBase, coinPrice, amount);
					
					Thread.sleep(800);
					
					logger.info("=======>步骤5: 提交卖单");
					// 提交卖单
					retrySellLimitOrder(coinBase, coinPrice, amount);
					
				} else {
					logger.info("价格下跌，先卖后买 current:{} last:{}", currentPriceOrder, lastPriceOrder);
					logger.info("=======>步骤4: 提交卖单");
					// 提交卖单
					retrySellLimitOrder(coinBase, coinPrice, amount);
					
					Thread.sleep(800);
					
					logger.info("=======>步骤5: 提交买单");
					// 提交买单
					retryBuyLimitOrder(coinBase, coinPrice, amount);
				}
				
				// 暂停3秒
				Thread.sleep(3000);
			}
		}
		
		Thread.sleep(5000);
		logger.info("============================交易结束{}{}============================", coinBase, coinPrice);
		logger.info("============================交易开始balance============================");
		printBalance();
		
		logger.info("============================交易结束balance============================");
		retryBalance();
		printBalance();
	}
	
	// 得到服务器时间
	public static long getServerTime() throws Exception {
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "public/server-time";
		requestBuilder.url(urlStr);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
//                logger.info("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
			if( object.getIntValue("status") == 0 ) {
				response.body().close();
				return object.getLongValue("data");
			} else {
					logger.error("出错:" + object.getString("msg"));
				}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return 0;
	}
	
	//  得到余额
//	{  
//	   "status":0,
//	   "data":[  
//	      {  
//	         "currency":"usdt",
//	         "available":"0.000000000000000000",
//	         "frozen":"0.000000000000000000",
//	         "balance":"0.000000000000000000"
//	      },
//	      {  
//	         "currency":"ft",
//	         "available":"10000.000000000000000000",
//	         "frozen":"0.000000000000000000",
//	         "balance":"10000.000000000000000000"
//	      }
//	   ]
//	}
	
	public static void retryBalance() {
		
		Callable<Boolean> task = new Callable<Boolean>() {
		    public Boolean call() throws Exception {
		        return getBalance();
		    }
		};
		
		try {
			retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static boolean getBalance() throws Exception {
		long currentTime = System.currentTimeMillis();
		String sign = getSignString("GET", "accounts/balance", currentTime, null);
		
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "accounts/balance";
		requestBuilder.url(urlStr);
		requestBuilder.addHeader("FC-ACCESS-KEY", key);
		requestBuilder.addHeader("FC-ACCESS-SIGNATURE", sign);
		requestBuilder.addHeader("FC-ACCESS-TIMESTAMP", String.valueOf(currentTime));
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
    			if( object.getIntValue("status") == 0 ) {
    				balance.clear();
    				
    				JSONArray array = object.getJSONArray("data");
    				for( int i=0; i<array.size(); i++ ) {
    					JSONObject oi = array.getJSONObject(i);
    					Balance b = new Balance(oi.getString("currency"), oi.getDouble("available"), oi.getDouble("frozen"), oi.getDouble("balance"));
    					balance.put(oi.getString("currency"), b);
//    					logger.debug(b.toString());
    				}
    				
    				response.body().close();
    				return true;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return false;
	}
	
	public static void retryTicker(String coinBase, String coinPrice) {
		
		Callable<Boolean> task = new Callable<Boolean>() {
		    public Boolean call() throws Exception {
		        return getTicker(coinBase, coinPrice);
		    }
		};
		
		try {
			retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// 得到最新的买一卖一价格，算挂单价格
//	{
//	  "status": 0,
//	  "data": {
//	    "ticker": [
//	      0.611284000,
//	      65.650000000,
//	      0.611271000,
//	      15.000000000,
//	      0.611284000,
//	      1213.920000000,
//	      0.783558000,
//	      0.809988000,
//	      0.580000000,
//	      1253085570.146541527,
//	      891938892.350288802378223000
//	    ],
//	    "type": "ticker.ftusdt",
//	    "seq": 19449234
//	  }
//	}
	public static boolean getTicker(String coinBase, String coinPrice) throws IOException {
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "market/ticker/" + coinBase + coinPrice;
		requestBuilder.url(urlStr);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
            
    			if( object.getIntValue("status") == 0 ) {
    				String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
    				
    				JSONArray array = object.getJSONObject("data").getJSONArray("ticker");
    				Ticker ticker = new Ticker(coinBase, 
    						coinPrice, 
    						array.getBigDecimal(0), 
    						array.getBigDecimal(4), 
    						array.getBigDecimal(5), 
    						array.getBigDecimal(2), 
    						array.getBigDecimal(3),
    						array.getBigDecimal(7),
    						array.getBigDecimal(8));
    				currentTicker = ticker;
    				
    				response.body().close();
    				return true;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return false;
	}
	
	public static void retryDepth(String coinBase, String coinPrice) {
		
		Callable<Boolean> task = new Callable<Boolean>() {
		    public Boolean call() throws Exception {
		        return getDepth(coinBase, coinPrice);
		    }
		};
		
		try {
			retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// 深度数据
//	{
//		  "status": 0,
//		  "data": {
//		    "bids": [
//		      0.530357000,
//		      3952.403848900,
//		      0.530303000,
//		      3339.770000000,
//		    ],
//		    "asks": [
//		      0.531720000,
//		      12987.113445000,
//		      0.531721000,
//		      688.420000000,
//		    ],
//		    "ts": 1529571928012,
//		    "seq": 24233561,
//		    "type": "depth.L20.ftusdt"
//		  }
//		}
	public static boolean getDepth(String coinBase, String coinPrice) throws IOException {
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "market/depth/L20/" + coinBase + coinPrice;
		requestBuilder.url(urlStr);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
            
    			if( object.getIntValue("status") == 0 ) {
    				String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
    				
    				JSONObject dataObject = object.getJSONObject("data");
    				
    				JSONArray bidsArray = dataObject.getJSONArray("bids");
    				JSONArray asksArray = dataObject.getJSONArray("asks");
    				
    				ArrayList<BigDecimal >bids = new ArrayList<>();
    				ArrayList<BigDecimal> asks = new ArrayList<>();
    				for( int i=0; i<bidsArray.size(); i++) {
    					bids.add(bidsArray.getBigDecimal(i));
    				}
    				
    				for( int i=0; i<asksArray.size(); i++) {
    					asks.add(asksArray.getBigDecimal(i));
    				}
    				
    				currentDepth = new Depth(coinBase, coinPrice, bids, asks);
    				
    				response.body().close();
    				return true;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return false;
	}
	
	public static String retryGenerateOrder(String coinBase, String coinPrice, String amount, String side, String type) {
		
		Callable<String> task = new Callable<String>() {
		    public String call() throws Exception {
		    		if( side.equals("sell") ) {
		    			if( type.equals("limit") ) {
		    				return generateOrder(coinBase, coinPrice, amount, "sell", "limit");
		    			} else {
		    				return generateOrder(coinBase, coinPrice, amount, "sell", "market");
		    			}
		    		} else {
		    			if( type.equals("limit") ) {
		    				return generateOrder(coinBase, coinPrice, amount, "buy", "limit");
		    			} else {
		    				return generateOrder(coinBase, coinPrice, amount, "buy", "market");
		    			}
		    		}
		        
		    }
		};
		
		try {
			return tradeRetryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static String generateOrder(String coinBase, String coinPrice, String amount, String side, String type ) throws Exception {
		long currentTime = System.currentTimeMillis();
		String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
		
		HashMap<String, String> parameters = new HashMap<String, String>();
		parameters.put("symbol", coinBase + coinPrice);
		parameters.put("side", side);
		parameters.put("type", type);
		parameters.put("amount", amount);
		
		// 限价单
		BigDecimal price=null;
		if( type.equals("limit") ) {
			price = getPrice(coinBase, coinPrice, side);
			parameters.put("price", price.toString());
		}
		
		String sign = getSignString("POST", "orders", currentTime, parameters);
		
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "orders";
		requestBuilder.url(urlStr);
		requestBuilder.addHeader("FC-ACCESS-KEY", key);
		requestBuilder.addHeader("FC-ACCESS-SIGNATURE", sign);
		requestBuilder.addHeader("FC-ACCESS-TIMESTAMP", String.valueOf(currentTime));
		
		// 设置参数
		String jsonString = JSON.toJSONString(parameters);
		RequestBody requestBody = RequestBody.create(MEDIATYPE_JSON, jsonString);
		requestBuilder.post(requestBody);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
    			JSONObject object = JSONObject.parseObject(result);
    			if( object.getIntValue("status") == 0 ) {
    				String orderId = object.getString("data");
    				if( type.equals("limit") ) {
    					logger.info("下单完成:{} {} {} 价格:{} amount:{}", orderId, type, side, price, amount);
    				} else {
    					logger.info("下单完成:{} {} {} amount:{}", orderId, type, side, amount);
    				}
    				
    				response.body().close();
    				return orderId;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return null;
	}
	
	public static String retrySellLimitOrder(String coinBase, String coinPrice, String amount) {
		return retryGenerateOrder(coinBase, coinPrice, amount, "sell", "limit");
	}
	
	public static String retrySellMarketOrder(String coinBase, String coinPrice, String amount) {
		return retryGenerateOrder(coinBase, coinPrice, amount, "sell", "market");
	}
	
	public static String retryBuyLimitOrder(String coinBase, String coinPrice, String amount) {
		return retryGenerateOrder(coinBase, coinPrice, amount, "buy", "limit");
	}
	
	public static String retryBuyMarketOrder(String coinBase, String coinPrice, String amount) {
		return retryGenerateOrder(coinBase, coinPrice, amount, "buy", "market");
	}
	
	// 得到所有未交易的订单
	public static List<String> getNotTradeAllOrders() throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();
		
		List<String> buyList = getNotTradeOrders("buy");
		List<String> sellList = getNotTradeOrders("sell");
		
		resultList.addAll(buyList);
		resultList.addAll(sellList);
		
		return resultList;
	}
	
	// 得到所有未交易的订单
	public static List<String> getNotTradeOrders(String side) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();
		
		List<String> submittedList = getOrders("submitted", "0", "100", side);
		List<String> partialList = getOrders("partial_filled", "0", "100", side);
		
		resultList.addAll(submittedList);
		resultList.addAll(partialList);
		
		return resultList;
	}
	
	// 得到订单信息
	public static List<String> getOrders( String states, String after, String limit, String side) throws Exception {
		ArrayList<String> resultList = new ArrayList<String>();
    		long currentTime = System.currentTimeMillis();
    		String symbol = coinBase + coinPrice;
		
    		String requestUrl = "orders?after=" + after + "&limit=" + limit + "&states=" + states + "&symbol=" + symbol;
		String sign = getSignString("GET", requestUrl, currentTime, null);
		
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + requestUrl;
		requestBuilder.url(urlStr);
		requestBuilder.addHeader("FC-ACCESS-KEY", key);
		requestBuilder.addHeader("FC-ACCESS-SIGNATURE", sign);
		requestBuilder.addHeader("FC-ACCESS-TIMESTAMP", String.valueOf(currentTime));
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
    			if( object.getIntValue("status") == 0 ) {
    				JSONArray array = object.getJSONArray("data");
    				for( int i=0; i<array.size(); i++) {
    					JSONObject od = array.getJSONObject(i);
    					resultList.add(od.getString("id"));
    				}
    				
    				response.body().close();
    				return resultList;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
		
		response.body().close();
		return resultList;
    }
	
	public static void cancelAllNotTradeOrders() throws Exception {
		List<String> orders = getNotTradeAllOrders();
		
		for( int i=0; i<orders.size(); i++) {
			retryCancelOrder(orders.get(i));
		}
	}
	
	public static void retryCancelOrder(String orderId) {
		
		Callable<Boolean> task = new Callable<Boolean>() {
		    public Boolean call() throws Exception {
		        return cancelOrder(orderId);
		    }
		};
		
		try {
			retryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// 取消订单
	public static boolean cancelOrder(String orderId) throws Exception {
		long currentTime = System.currentTimeMillis();
		HashMap<String, String> parameters = new HashMap<String, String>();
		parameters.put("order_id", orderId);
		
		String requestUrl = "orders/"+ orderId +"/submit-cancel";
		String sign = getSignString("POST", requestUrl, currentTime, null);
		
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + requestUrl;
		requestBuilder.url(urlStr);
		requestBuilder.addHeader("FC-ACCESS-KEY", key);
		requestBuilder.addHeader("FC-ACCESS-SIGNATURE", sign);
		requestBuilder.addHeader("FC-ACCESS-TIMESTAMP", String.valueOf(currentTime));
		
		// 设置参数
		RequestBody requestBody = RequestBody.create(MEDIATYPE_JSON, "");
		requestBuilder.post(requestBody);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
    			if( object.getIntValue("status") == 0) {
    				logger.info("订单取消成功:{}", orderId);
    				
    				response.body().close();
    				return true;
    			} else {
    				logger.error("订单取消失败:{} {}", orderId, object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return false;
    }
	
	public static Order retryOrder(String orderId) {
		
		Callable<Object> task = new Callable<Object>() {
		    public Order call() throws Exception {
		        return getOrder(orderId);
		    }
		};
		
		try {
			return (Order) objRetryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
//	{
//	  "status": 0,
//	  "data": {
//	    "id": "9d17a03b852e48c0b3920c7412867623",
//	    "symbol": "string",
//	    "type": "limit",
//	    "side": "buy",
//	    "price": "string",
//	    "amount": "string",
//	    "state": "submitted",
//	    "executed_value": "string",
//	    "fill_fees": "string",
//	    "filled_amount": "string",
//	    "created_at": 0,
//	    "source": "web"
//	  }
//	}
	public static Order getOrder(String orderId) throws Exception {
		long currentTime = System.currentTimeMillis();
		String requestUrl = "orders/" + orderId;
		String sign = getSignString("GET", requestUrl, currentTime, null);
		
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + requestUrl;
		requestBuilder.url(urlStr);
		requestBuilder.addHeader("FC-ACCESS-KEY", key);
		requestBuilder.addHeader("FC-ACCESS-SIGNATURE", sign);
		requestBuilder.addHeader("FC-ACCESS-TIMESTAMP", String.valueOf(currentTime));
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
    			if( object.getIntValue("status") == 0 ) {
    				balance.clear();
    				
    				JSONObject obj = object.getJSONObject("data");
    				Order order = new Order(orderId, 
    						coinBase, 
    						coinPrice, 
    						obj.getString("symbol"),
    						obj.getString("type"),
    						obj.getString("side"), 
    						obj.getBigDecimal("price"), 
    						obj.getBigDecimal("amount"), 
    						obj.getString("state"),
    						obj.getBigDecimal("executed_value"),
    						obj.getBigDecimal("fill_fees"),
    						obj.getBigDecimal("filled_amount"),
    						obj.getBigDecimal("created_at"),
    						obj.getString("source"));
    				
    				logger.debug(order.toString());
    				
    				response.body().close();
    				return order;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return null;
	}
	
	// 得到最近几个蜡烛图
	public static List<Candle> retryCandle(String candleResolution, int limit) {
		
		Callable<Object> task = new Callable<Object>() {
		    public List<Candle> call() throws Exception {
		        return getCandle(candleResolution, limit);
		    }
		};
		
		try {
			return (List<Candle>) objRetryer.call(task);
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RetryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static List<Candle> getCandle(String candleResolution, int limit) throws IOException {
		Request.Builder requestBuilder = new Request.Builder();
		String urlStr = baseUrl + "market/candles/"+ candleResolution +"/" + coinBase + coinPrice +"?limit=" + limit;
		requestBuilder.url(urlStr);
		
		Request request = requestBuilder.build();
		
		String result = "";
        Response response = client.newCall(request).execute();

        if( response.isSuccessful() ) {
            result = response.body().string();
            logger.debug("请求response:" + result);
            
            JSONObject object = JSONObject.parseObject(result);
            
    			if( object.getIntValue("status") == 0 ) {
    				String symbol = FcoinUtils.getSymbol(coinBase, coinPrice);
    				
    				JSONArray array = object.getJSONArray("data");
    				
    				ArrayList<Candle> resultList = new ArrayList<>();
    				for( int i=0; i<limit && limit<=array.size(); i++) {
    					JSONObject cd = array.getJSONObject(i);
    					
    					Candle candle = new Candle(
    							cd.getLongValue("id"),
    							candleResolution, 
    							cd.getBigDecimal("open"), 
    							cd.getBigDecimal("close"),
    							cd.getBigDecimal("high"), 
    							cd.getBigDecimal("low"));
    					
    					resultList.add(candle);
    				}
    				
    				response.body().close();
    				return resultList;
    			} else {
    				logger.error("出错:" + object.getString("msg"));
    			}
        } else {
            logger.error("网络错误:" + response.code() + ", url: " + urlStr);
        }
        
        response.body().close();
        return null;
	}
}
