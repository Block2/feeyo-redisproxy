package com.feeyo.redis.net.backend.callback;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.feeyo.redis.kafka.codec.ResponseHeader;
import com.feeyo.redis.net.backend.RedisBackendConnection;
import com.feeyo.redis.net.front.RedisFrontConnection;
import com.feeyo.redis.nio.NetSystem;

public abstract class KafkaCmdCallback extends AbstractBackendCallback {
	
	protected static final byte ASTERISK = '*';
	protected static final byte DOLLAR = '$';
	protected static final byte[] CRLF = "\r\n".getBytes();		
	protected static final byte[] OK =   "+OK\r\n".getBytes();
	protected static final byte[] NULL =   "$-1\r\n".getBytes();
	
	private byte[] buffer;
	
	@Override
	public void handleResponse(RedisBackendConnection conn, byte[] byteBuff) throws IOException {
		// 防止断包
		this.append(byteBuff);
		
		if ( !this.isComplete() ) {
			return;
		}
		
		ByteBuffer buffer = NetSystem.getInstance().getBufferPool().allocate( this.buffer.length );
		try {
			// 去除头部的长度
			buffer.put(this.buffer, 4, this.buffer.length - 4);
			buffer.flip();
			
			this.buffer = null;
			ResponseHeader responseHeader = ResponseHeader.parse(buffer);
			handle(buffer);
			
			RedisFrontConnection frontCon = getFrontCon( conn );
			// 后段链接释放
			conn.release();	
			frontCon.releaseLock();
			// 数据收集
//				StatUtil.collect(password, cmd, key, requestSize, responseSize, (int)(responseTimeMills - requestTimeMills), false);
			
		} catch (Exception e) {
		} finally {
			NetSystem.getInstance().getBufferPool().recycle(buffer);
			
		}
		
	}
	public abstract void handle(ByteBuffer buffer);

	private void append(byte[] buf) {
		if (buffer == null) {
			buffer = buf;
		} else {
			byte[] newBuffer = new byte[this.buffer.length + buf.length];
			System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
			System.arraycopy(buf, 0, newBuffer, buffer.length, buf.length);
			this.buffer = newBuffer;
			newBuffer = null;
			buf = null;
		}
	}
	
	private boolean isComplete() {
		int len = this.buffer.length;
		if (len < 4) {
			return false;
		}
		int v0 = (this.buffer[0] & 0xff) << 24;
		int v1 = (this.buffer[1] & 0xff) << 16;  
		int v2 = (this.buffer[2] & 0xff) << 8;  
	    int v3 = (this.buffer[3] & 0xff); 
	    
	    if (v0 + v1 + v2 + v3 > len - 4) {
	    		return false;
	    }
		
		return true;
	}
}
