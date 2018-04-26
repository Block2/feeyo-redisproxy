package com.feeyo.redis.nio;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.feeyo.redis.nio.util.TimeUtil;


/**
 * 流量监控
 *
 */
public class NetFlowMonitor {
	
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
	
	private volatile boolean overproof = false;
	
	private AtomicLong[] net;
	private volatile int index;
	private boolean isOpen;
	private final long size;
	
	public NetFlowMonitor(long size) {
		this.size = size;
		this.isOpen = this.size >= 0;
		// 开启流量控制
		if (isOpen) {
			this.net = new AtomicLong[60];
			for (int i = 0; i < this.net.length; i++)
				this.net[i] = new AtomicLong(size);
			
			this.start();
		}
	}
		
	private void start() {
		
		executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				// 更新当前坐标
				index = getIndex();

				// 判定是否需要限流
				if (index == 0) {
					overproof = net[59].get() < 0;
				} else {
					overproof = net[index - 1].get() < 0;
				}
				
				// 更新其他容量
				for (int i = 0; i < net.length; i++) {
					if (i != index) {
						net[i].set(size);
					}
				}
			}
		}, 0, 1, TimeUnit.SECONDS);
	}
	
	public boolean pool(long size) {
		if (isOpen) {
			return decrement(this.net[index], size) > 0;
		}
		return true;
	}
	
	public boolean isOverproof() {
		return overproof;
	}
	
	private int getIndex() {
		long currentTimeMillis = TimeUtil.currentTimeMillis();
        return (int) ((currentTimeMillis / 1000) % 60);
	}
	
    private final long decrement(AtomicLong al, long delta) {
        for (;;) {
            long current = al.get();
            long next = current - delta;
            if (al.compareAndSet(current, next))
                return next;
        }
    }
}
