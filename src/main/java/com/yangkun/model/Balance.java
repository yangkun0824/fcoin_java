package com.yangkun.model;

public class Balance {
	
	private String currency;
	
	public Balance(String currency, double available, double frozen, double balance) {
		super();
		this.currency = currency;
		this.available = available;
		this.frozen = frozen;
		this.balance = balance;
	}

	private double available;
    
    private double frozen;
    
    private double balance;

    @Override
	public String toString() {
		return "Balance [currency=" + currency + ", available=" + available + ", frozen=" + frozen + ", balance="
				+ balance + "]";
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public double getAvailable() {
        return available;
    }

    public void setAvailable(double available) {
        this.available = available;
    }

    public double getFrozen() {
        return frozen;
    }

    public void setFrozen(double frozen) {
        this.frozen = frozen;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

}
