package com.feeyo.redis.nio;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.redis.net.backend.RedisBackendConnection;
import com.feeyo.redis.nio.buffer.BufferPool;
import com.feeyo.redis.nio.util.TimeUtil;


/**
 * 存放当前所有连接的信息，包括客户端和服务端等，以及Network部分所使用共用对象
 *
 * @author wuzhih
 *
 */
public class NetSystem {
	
	private static Logger LOGGER = LoggerFactory.getLogger( "Connection" );
	
	public static final int RUNNING = 0;
	public static final int SHUTING_DOWN = -1;
	
	private static NetSystem INSTANCE;
	private final BufferPool bufferPool;
	
	// 用来执行那些耗时的任务
	private final NameableExecutor businessExecutor;
	
	// 用来执行定时任务
	private final NameableExecutor timerExecutor;
	
	private final int TIMEOUT = 1000 * 60 * 5; //5分钟
	
	private final ConcurrentHashMap<Long, Connection> allConnections;
	private SystemConfig netConfig;
	private NIOConnector connector;

	public static NetSystem getInstance() {
		return INSTANCE;
	}

	public NetSystem(BufferPool bufferPool,  NameableExecutor businessExecutor, NameableExecutor timerExecutor)
			throws IOException {
		this.bufferPool = bufferPool;
		this.businessExecutor = businessExecutor;
		this.timerExecutor = timerExecutor;
		this.allConnections = new ConcurrentHashMap<Long, Connection>();
		INSTANCE = this;
	}

	public BufferPool getBufferPool() {
		return bufferPool;
	}

	public NIOConnector getConnector() {
		return connector;
	}

	public void setConnector(NIOConnector connector) {
		this.connector = connector;
	}

	public SystemConfig getNetConfig() {
		return netConfig;
	}

	public void setNetConfig(SystemConfig netConfig) {
		this.netConfig = netConfig;
	}

	public NameableExecutor getBusinessExecutor() {
		return businessExecutor;
	}

	public NameableExecutor getTimerExecutor() {
		return timerExecutor;
	}

	/**
	 * 添加一个连接到系统中被监控
	 */
	public void addConnection(Connection c) {
		
		if ( LOGGER.isDebugEnabled() ) {
			LOGGER.debug("add:" + c);
		}
		
		allConnections.put(c.getId(), c);
	}
	
	public void removeConnection(Connection c) {
		
		if ( LOGGER.isDebugEnabled() ) {
			LOGGER.debug("remove:" + c);
		}
		
		this.allConnections.remove( c.getId() );
	}

	public ConcurrentMap<Long, Connection> getAllConnectios() {
		return allConnections;
	}

	
	
	/**
	 * 定时执行该方法，回收部分资源。
	 */
	public void checkConnections() {
		Iterator<Entry<Long, Connection>> it = allConnections.entrySet().iterator();
		while (it.hasNext()) {
			Connection c = it.next().getValue();
			// 删除空连接
			if (c == null) {
				it.remove();
				continue;
			}

			// 后端超时的连接关闭
			if ( c instanceof RedisBackendConnection ) {
				RedisBackendConnection backendCon = (RedisBackendConnection)c;
				if (backendCon.isBorrowed() && backendCon.getLastTime() < TimeUtil.currentTimeMillis() - TIMEOUT ) {
					
					StringBuffer errBuffer = new StringBuffer();
					errBuffer.append("backend timeout, close it " ).append( c );
					if ( c.getAttachement() != null ) {
						errBuffer.append(" , and attach it " ).append( c.getAttachement() );
					}
					errBuffer.append( " , channel isConnected: " ).append(backendCon.getChannel().isConnected());
			        errBuffer.append( " , channel isBlocking: " ).append(backendCon.getChannel().isBlocking());
			        errBuffer.append( " , channel isOpen: " ).append(backendCon.getChannel().isOpen());
			        errBuffer.append( " , socket isConnected: " ).append(backendCon.getChannel().socket().isConnected());
			        errBuffer.append( " , socket isClosed: " ).append(backendCon.getChannel().socket().isClosed());
			        
					LOGGER.warn( errBuffer.toString() );
					
					c.close("backend timeout");
				}
			}
			
			// 清理已关闭连接，否则空闲检查。
			if (c.isClosed()) {
				it.remove();
			} else {

				// very important ,for some data maybe not sent
				if ( c.isConnected() && !c.writeQueue.isEmpty() ) {
					c.doNextWriteCheck();
				}

				c.idleCheck();
			}
		}
	}
	
	public void setSocketParams(Connection con, boolean isFrontChannel) throws IOException {
		//int sorcvbuf = 0;
		int sosndbuf = 0;
		
		if (isFrontChannel) {
			//sorcvbuf = netConfig.getFrontsocketsorcvbuf();
			sosndbuf = netConfig.getFrontsocketsosndbuf();
		} else {
			//sorcvbuf = netConfig.getBacksocketsorcvbuf();
			sosndbuf = netConfig.getBacksocketsosndbuf();
		}
		
		NetworkChannel channel = con.getChannel();
		
		// LINUX 2.6 该 RCVBUF 会自动调节
		//channel.setOption(StandardSocketOptions.SO_RCVBUF, sorcvbuf);
		channel.setOption(StandardSocketOptions.SO_SNDBUF, sosndbuf);
		channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
		channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		
	}
}