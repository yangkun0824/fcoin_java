package com.yangkun.fcoin;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.AttemptTimeLimiters;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class RetryTest {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Callable<Boolean> task = new Callable<Boolean>() {
			
		    public Boolean call() throws Exception {
		        return false;
		    }
		};

//		Retryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
//		        .retryIfResult(Predicates.<Integer>isNull())
//		        .retryIfResult(Predicates.equalTo(2))
//		        .retryIfExceptionOfType(IOException.class)
//		        .withStopStrategy(StopStrategies.stopAfterAttempt(3))
//		        .withWaitStrategy(WaitStrategies.fixedWait(3000, TimeUnit.MILLISECONDS))
//		        .build();
		
		Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
				.retryIfResult( r -> r.equals(false))
		        .retryIfException()
		        .withStopStrategy(StopStrategies.stopAfterAttempt(3))
		        .withWaitStrategy(WaitStrategies.fixedWait(3000, TimeUnit.MILLISECONDS))
		        .withRetryListener(new RetryListener() {
		            public <V> void onRetry(Attempt<V> attempt) {
		                if (attempt.hasException()){
		                    attempt.getExceptionCause().printStackTrace();
		                }
		                System.out.println(attempt.getResult());
		            }
		        })
		        .build();
		
		try {
		    retryer.call(task);
		} catch (ExecutionException e) {
		    e.printStackTrace();
		} catch (RetryException e) {
		    e.printStackTrace();
		}

	}
}
