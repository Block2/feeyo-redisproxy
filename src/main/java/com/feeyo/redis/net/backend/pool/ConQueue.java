package com.feeyo.redis.net.backend.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.feeyo.redis.net.backend.BackendConnection;
import com.feeyo.redis.net.backend.RedisBackendConnection;
import com.feeyo.redis.nio.AbstractConnection;
import com.feeyo.redis.nio.NetSystem;

public class ConQueue {

	//后端连接
	private final ConcurrentLinkedQueue<BackendConnection> cons = new ConcurrentLinkedQueue<BackendConnection>();

	public BackendConnection takeIdleCon() {		
		ConcurrentLinkedQueue<BackendConnection> f1 = cons;
		BackendConnection con = f1.poll();
		if (con == null || con.isClosed() || !con.isConnected() ) {
			return null;
		} else {
			return con;
		}
	}

	public void removeCon(BackendConnection con) {
		cons.remove(con);
	}

	public ConcurrentLinkedQueue<BackendConnection> getCons() {
		return cons;
	}

	public ArrayList<BackendConnection> getIdleConsToClose(int count) {
		ArrayList<BackendConnection> readyCloseCons = new ArrayList<BackendConnection>(count);
		while (!cons.isEmpty() && readyCloseCons.size() < count) {
			BackendConnection theCon = cons.poll();
			if (theCon != null) {
				readyCloseCons.add(theCon);
			}
		}
		return readyCloseCons;
	}	
	
	public int getActiveCountForNode(PhysicalNode node) {
        int total = 0;
        for (AbstractConnection conn : NetSystem.getInstance().getAllConnectios().values()) {
            if (conn instanceof RedisBackendConnection) {
            	RedisBackendConnection theCon = (RedisBackendConnection) conn;
                if (theCon.getPhysicalNode() == node) {
                    if (theCon.isBorrowed()) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    public void clearConnections(String reason, PhysicalNode node) {
        Iterator<Entry<Long, AbstractConnection>> itor = NetSystem.getInstance().getAllConnectios().entrySet().iterator();
        while ( itor.hasNext() ) {
            Entry<Long, AbstractConnection> entry = itor.next();
            AbstractConnection con = entry.getValue();
            if (con instanceof RedisBackendConnection) {
                if (((RedisBackendConnection) con).getPhysicalNode() == node) {
                    con.close(reason);
                    itor.remove();
                }
            }
        }
    }
    
    public void setIdleTimeConnections(PhysicalNode node, long idleTimeout) {    	
        Iterator<Entry<Long, AbstractConnection>> itor = NetSystem.getInstance().getAllConnectios().entrySet().iterator();
        while ( itor.hasNext() ) {
            Entry<Long, AbstractConnection> entry = itor.next();
            AbstractConnection con = entry.getValue();
            if (con instanceof RedisBackendConnection) {
                if (((RedisBackendConnection) con).getPhysicalNode() == node) {
                	con.setIdleTimeout( idleTimeout );
                }
            }
        }
    }
}