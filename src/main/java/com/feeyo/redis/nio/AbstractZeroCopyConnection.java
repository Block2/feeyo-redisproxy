package com.feeyo.redis.nio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.redis.nio.util.MappedByteBufferUtil;
import com.feeyo.redis.nio.util.TimeUtil;

/**
 * ZeroCopy
 * 
 * @author zhuam
 */
public abstract class AbstractZeroCopyConnection extends AbstractConnection {
	
	private static Logger LOGGER = LoggerFactory.getLogger( AbstractZeroCopyConnection.class );
	
	private static final int TOTAL_SIZE = 1024 * 1024 * 1;  
	
	//
	private RandomAccessFile randomAccessFile;
	protected FileChannel fileChannel;
	private MappedByteBuffer mappedByteBuffer;

	
	// r/w lock
	protected AtomicBoolean rwLock = new AtomicBoolean( false );
	
	// OS
	private static int OS_TYPE = -1;
	private static final int WIN = 1;
	private static final int MAC = 2;
	private static final int LINUX = 3;

	static {
		String osName = System.getProperty("os.name").toUpperCase();
		if ( osName.startsWith("WIN") ) {
			OS_TYPE  = WIN;
		} else if ( osName.startsWith("MAC") ) {
			OS_TYPE = MAC;
		} else {
			OS_TYPE = LINUX;
		}
	}
	
	
	public AbstractZeroCopyConnection(SocketChannel channel) {

		super(channel);

		try {
			
			String path = null;
			if ( OS_TYPE == WIN ) {
				path = "c:/";
				
			} else if ( OS_TYPE == MAC ) {
				path = "";
				
			} else if ( OS_TYPE == LINUX ) {
				path = "/dev/shm/";
			}
			String filename = path + id + ".mapped";
			
			this.randomAccessFile = new RandomAccessFile(filename, "rw");
			this.randomAccessFile.setLength(TOTAL_SIZE);
			this.randomAccessFile.seek(0);

			this.fileChannel = randomAccessFile.getChannel();
			this.mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, TOTAL_SIZE);

		} catch (IOException e) {
			LOGGER.error("create mapped err:", e);
		}
		
	}

	/**
	 * 异步读取,该方法在 reactor 中被调用
	 */
	@Override
	protected void asynRead() throws IOException {
		
		if (isClosed.get()) {
			return;
		}
		
		// rw 进行中
		if ( !rwLock.compareAndSet(false, true) ) {
			return;
		}
				
		//
		lastReadTime = TimeUtil.currentTimeMillis();
		
		try {
			
			// 循环处理字节信息
			for(;;) {
				
				final int position = mappedByteBuffer.position();
				final int count    = TOTAL_SIZE - position;
				int tranfered = (int) fileChannel.transferFrom(channel, position, count);
				mappedByteBuffer.position(position + tranfered);
				
				// fixbug: transferFrom() always return 0 when client closed abnormally!
				// --------------------------------------------------------------------
				// So decide whether the connection closed or not by read()! 
				if( tranfered == 0 && count > 0 ){
					tranfered = channel.read(mappedByteBuffer);
				}
				
				switch ( tranfered ) {
				case 0:
					if (!this.channel.isOpen()) {
						this.close("socket closed");
						return;
					}
					
					// not enough space
					// 
					
					break;
				case -1:
					this.close("stream closed");
					break;
				default:
					
					//
					// 负责解析报文并处理
					
					break;
				}
			}
			
		} finally {
			rwLock.set( false );
		}
		
	}

	@Override
	public void write(byte[] buf) {
		
		// TODO 
		// 1、考虑 rwLock 自旋
		// 2、考虑 buf size 大于 mappedByteBuffer 的情况，分块
		// 3、 write0 确保写OK
		
		mappedByteBuffer.put(buf);

		try {
			write0(0,0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void write(ByteBuffer buf) {
		
		try {
			
			int position = mappedByteBuffer.position();
			int writed = fileChannel.write(mappedByteBuffer, position);
			
			if (buf.hasRemaining()) {
				throw new IOException("can't write whole buf ,writed " + writed + " remains " + buf.remaining());
			}
			mappedByteBuffer.position(position + writed);
			
			write0(0, 0);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void write0(long position, long count) throws IOException {
		//
		//
		long written = fileChannel.transferTo(position, count, channel);
		
		boolean noMoreData = ( written == count );
		if (noMoreData) {
		    if ((processKey.isValid() && (processKey.interestOps() & SelectionKey.OP_WRITE) != 0)) {
		        disableWrite();
		    }

		} else {
		    if ((processKey.isValid() && (processKey.interestOps() & SelectionKey.OP_WRITE) == 0)) {
		        enableWrite(false);
		    }
		}
		
	}

	@Override
	public void doNextWriteCheck() {
		// ignore
	}
	
	
	@Override
	protected void cleanup() {
		
		// clear file
		try {
			
			if ( mappedByteBuffer != null ) {
				mappedByteBuffer.force();
				MappedByteBufferUtil.clean(mappedByteBuffer);
			}
			
			if ( fileChannel != null)
				fileChannel.close();
			
			if ( randomAccessFile != null )
				randomAccessFile.close();
			
		} catch (IOException e) {
			LOGGER.error("close file error", e);
		}		
	}

}
