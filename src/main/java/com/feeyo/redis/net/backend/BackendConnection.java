package com.feeyo.redis.net.backend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import com.feeyo.redis.net.backend.callback.BackendCallback;
import com.feeyo.redis.net.backend.pool.PhysicalNode;
import com.feeyo.redis.nio.ClosableConnection;
import com.feeyo.redis.nio.Connection;
import com.feeyo.redis.nio.NIOHandler;
import com.feeyo.redis.nio.NetFlowMonitor;
import com.feeyo.redis.nio.ZeroCopyConnection;

/**
 * 后端连接
 * 
 * @author zhuam
 *
 */
public class BackendConnection extends ClosableConnection {
	
	private boolean isZeroCopy = false;
	private ClosableConnection delegateConn;

	protected BackendCallback callback;
    protected PhysicalNode physicalNode;
    
    protected volatile boolean borrowed = false;
    protected volatile long heartbeatTime = 0;	//心跳应答时间
    
    private volatile long lastTime;
    
	public BackendConnection(boolean isZeroCopy, SocketChannel socketChannel) {

		if ( isZeroCopy ) {
			delegateConn = new ZeroCopyConnection(socketChannel); 
		} else {
			delegateConn = new Connection(socketChannel);
		}
		
		this.isZeroCopy = true;
	}
	
	
	// 
	//
	//
	public BackendCallback getCallback() {
		return callback;
	}

	public void setCallback(BackendCallback callback) {
		this.callback = callback;
	}

	public PhysicalNode getPhysicalNode() {
		return physicalNode;
	}

	public void setPhysicalNode(PhysicalNode node) {
		this.physicalNode = node;
	}
	
	public void release() {
		this.setBorrowed( false );
		this.physicalNode.releaseConnection(this);
	}

	public void setBorrowed(boolean borrowed) {
		this.borrowed = borrowed;
	}
	
	public boolean isBorrowed() {
        return this.borrowed;
    }

	public long getHeartbeatTime() {
		return heartbeatTime;
	}

	public void setHeartbeatTime(long heartbeatTime) {
		this.heartbeatTime = heartbeatTime;
	}
	
	public long getLastTime() {
		return lastTime;
	}

	public void setLastTime(long currentTimeMillis) {
		this.lastTime = currentTimeMillis;
	}

	
	// delegate
	//
	//
	
	@Override
	public long getIdleTimeout() {
		return delegateConn.getIdleTimeout();
	}

	@Override
	public void setIdleTimeout(long idleTimeout) {
		delegateConn.setIdleTimeout(idleTimeout); 
	}
	
	@Override
	public String getHost() {
		return delegateConn.getHost();
	}

	@Override
	public void setHost(String host) {
		delegateConn.setHost(host);
	}

	@Override
	public int getPort() {
		return delegateConn.getPort();
	}

	@Override
	public void setPort(int port) {
		delegateConn.setPort(port);
	}

	@Override
	public long getId() {
		return delegateConn.getId();
	}

	@Override
	public int getLocalPort() {
		return delegateConn.getLocalPort();
	}

	@Override
	public void setLocalPort(int localPort) {
		delegateConn.setLocalPort(localPort);
	}

	@Override
	public void setId(long id) {
		delegateConn.setId(id);
	}

	@Override
	public boolean isIdleTimeout() {
		return delegateConn.isIdleTimeout();
	}

	@Override
	public SocketChannel getSocketChannel() {
		return delegateConn.getSocketChannel();
	}

	@Override
	public long getStartupTime() {
		return delegateConn.getStartupTime();
	}

	@Override
	public long getLastReadTime() {
		return delegateConn.getLastReadTime();
	}

	@Override
	public long getLastWriteTime() {
		return delegateConn.getLastWriteTime();
	}
	
	@Override
	public long getNetInBytes() {
		return delegateConn.getNetInBytes();
	}
	
	@Override
	public long getNetInCounter() {
		return delegateConn.getNetInCounter();
	}

	@Override
	public long getNetOutBytes() {
		return delegateConn.getNetOutBytes();
	}

	@Override
	public long getNetOutCounter() {
		return delegateConn.getNetOutCounter();
	}
	
	@Override
	public void setHandler(NIOHandler<? extends ClosableConnection> handler) {
		delegateConn.setHandler(handler);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public NIOHandler getHandler() {
		return delegateConn.getHandler();
	}
	
	@Override
	public void setNetFlowMonitor(NetFlowMonitor nfm) {
		delegateConn.setNetFlowMonitor(nfm);
	}

	@Override
	public boolean isConnected() {
		return delegateConn.isConnected();
	}

	// 
	//
	@Override
	public void close(String reason) {
		delegateConn.close(reason);
	}

	@Override
	public boolean isClosed() {
		return delegateConn.isClosed();
	}

	@Override
	public void idleCheck() {		
		delegateConn.idleCheck();
	}

	/**
	 * 清理资源
	 */
	@Override
	protected void cleanup() {
		// ignore
	}

	@Override
	public void register(Selector selector) throws IOException {
		delegateConn.register(selector);
	}
	
	@Override
	public void doNextWriteCheck() {
		delegateConn.doNextWriteCheck();
	}
	
	@Override
	public void write(byte[] data) {
		delegateConn.write(data);
	}
	
	@Override
	public void write(ByteBuffer data) {
		delegateConn.write(data);
	}
	
	@Override
	public void setReactor(String reactorName) {
		delegateConn.setReactor(reactorName);
	}

	@Override
	public String getReactor() {
		return delegateConn.getReactor();
	}

	@Override
	public boolean belongsReactor(String reacotr) {
		return delegateConn.belongsReactor(reacotr);
	}

	@Override
	public Object getAttachement() {
		return delegateConn.getAttachement();
	}

	@Override
	public void setAttachement(Object attachement) {
		delegateConn.setAttachement(attachement);
	}

	@Override
	public void setState(int newState) {
		delegateConn.setState(newState);
	}
	
	// 异步读取,该方法在 reactor 中被调用
	@Override
	public void asynRead() throws IOException {
		delegateConn.asynRead();
	}
	

	@Override
	public int getState() {
		return delegateConn.getState();
	}

	@Override
	public Direction getDirection() {
		return delegateConn.getDirection();
	}

	@Override
	public void setDirection(Direction in) {
		delegateConn.setDirection(in);
	}
	
	@Override
	public boolean isFlowLimit() {
		return delegateConn.isFlowLimit();
	}

	@Override
	public void flowClean() {
		delegateConn.flowClean();
	}
	
	//
	@Override
	public String toString() {
		StringBuffer sbuffer = new StringBuffer(100);
		sbuffer.append( "Con [reactor=").append( reactor );
		sbuffer.append(", host=").append( host ).append("/").append( port );
		sbuffer.append(", id=").append( id );
		sbuffer.append(", borrowed=").append( borrowed );
		sbuffer.append(", startup=").append( startupTime );
		sbuffer.append(", lastRT=").append( lastReadTime );
		sbuffer.append(", lastWT=").append( lastWriteTime );
		sbuffer.append(", attempts=").append( writeAttempts );	//
		sbuffer.append(", cc=").append( netInCounter ).append("/").append( netOutCounter );	//
		if ( heartbeatTime > 0 ) {
			sbuffer.append(", HT=").append( heartbeatTime );
		}
		
		if ( isClosed.get() ) {
			sbuffer.append(", isClosed=").append( isClosed );
			sbuffer.append(", closedTime=").append( closeTime );
			sbuffer.append(", closeReason=").append( closeReason );
		}
		
		// zero copy
		sbuffer.append(", isZeroCopy=").append( isZeroCopy );
		sbuffer.append("]");
		return  sbuffer.toString();
	}
	
}