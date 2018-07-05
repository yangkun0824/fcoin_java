package com.yangkun.fcoin;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class BigDecimalTest {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		double am = 5.0558000000000005;
		BigDecimal amBigDecimal = new BigDecimal(am);
		double am1 = amBigDecimal.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
		
		System.out.println(String.valueOf(am1));
		
		double d = 1.000;
		BigDecimal bd=new BigDecimal(d);
		double d1=bd.setScale(2,BigDecimal.ROUND_HALF_UP).doubleValue();
		System.out.println(d1);
	}

}
