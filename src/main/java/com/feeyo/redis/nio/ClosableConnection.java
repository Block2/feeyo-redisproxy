package com.feeyo.redis.nio;

public interface ClosableConnection {
	
	//关闭连接
	void close(String reason);
	boolean isClosed();
	
	public void idleCheck();

	long getStartupTime();

	String getHost();
	int getPort();
	int getLocalPort();
	
	long getNetInCounter();
	long getNetInBytes();
	
	long getNetOutCounter();
	long getNetOutBytes();

	// 限流 ( 限流开关、流量清洗 )
	//++++++++++++++++++++++++
	boolean isFlowLimit();	
	void flowClean();
	
}