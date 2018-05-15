package com.feeyo.kafka.net.backend.broker;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.kafka.config.KafkaPoolCfg;
import com.feeyo.kafka.config.OffsetManageCfg;
import com.feeyo.kafka.config.TopicCfg;
import com.feeyo.kafka.config.loader.KafkaConfigLoader;
import com.feeyo.kafka.util.JsonUtils;
import com.feeyo.redis.config.ConfigLoader;
import com.feeyo.redis.config.PoolCfg;
import com.feeyo.redis.engine.RedisEngineCtx;

/**
 * 管理 topic offset
 * 
 * @author yangtao
 */
public class RunningOffsetAdmin {
	
	private static Logger LOGGER = LoggerFactory.getLogger(RunningOffsetAdmin.class);
	
	private static final String ZK_CFG_FILE = "kafka.xml"; // zk settings is in server.xml
	

	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
	
	private static RunningOffsetAdmin INSTANCE = new RunningOffsetAdmin();

	private CuratorFramework curator;
	private OffsetManageCfg offsetManageCfg;
	
	
	public static RunningOffsetAdmin getInstance() {
		return INSTANCE;
	}
	
	private RunningOffsetAdmin() {

		offsetManageCfg = KafkaConfigLoader.loadOffsetManageCfg(ConfigLoader.buidCfgAbsPathFor(ZK_CFG_FILE));
		
		CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder().connectString(offsetManageCfg.getServer())
				.retryPolicy(new RetryNTimes(3, 1000)).connectionTimeoutMs(3000);

		curator = builder.build();

		curator.getConnectionStateListenable().addListener(new ConnectionStateListener() {
			@Override
			public void stateChanged(CuratorFramework client, ConnectionState state) {
				switch (state) {
				case CONNECTED:
					LOGGER.info("connected with zookeeper");
					break;
				case LOST:
					LOGGER.warn("lost session with zookeeper");
					break;
				case RECONNECTED:
					LOGGER.warn("reconnected with zookeeper");
					break;
				default:
					break;
				}
			}
		});
		curator.start();
		
		INSTANCE = this;
	}
	
	/**
	 * 创建节点
	 * @param path
	 */
	private void createZkNode(String path, byte[] data) {
		try {
			if (curator.checkExists().forPath(path) != null) {
				return;
			}
			int index = path.lastIndexOf('/');
			if (index > 0) {
				createZkNode(path.substring(0, index), null);
			}
			curator.create().forPath(path, data);
		} catch (Exception e) {
			LOGGER.warn("", e);
		}
	}
	
	
	private void savePartitionOffsets(String topicName,  Map<Integer, BrokerPartitionOffset> partitionOffsetMap, int poolId) {
		
		String basepath = offsetManageCfg.getPath() + File.separator + String.valueOf(poolId) + File.separator + topicName;
		Stat stat;
		try {
			for (Entry<Integer, BrokerPartitionOffset> entry : partitionOffsetMap.entrySet()) {
				
				// 点位
				BrokerPartitionOffset partitionOffset = entry.getValue();
				String path = basepath + File.separator + entry.getKey();
				stat = curator.checkExists().forPath(path);
				if (stat == null) {
					ZKPaths.mkdirs(curator.getZookeeperClient().getZooKeeper(), path);
				}
				curator.setData().inBackground().forPath(path, JsonUtils.marshalToByte( partitionOffset ));
				
				
				
				// 消费者点位
				Map<String, ConsumerOffset> consumerOffsets = partitionOffset.getConsumerOffsets();
				for (Entry<String, ConsumerOffset> consumerOffsetEntry : consumerOffsets.entrySet()) {
					
					String consumerOffsetPath = path + File.separator + consumerOffsetEntry.getKey();
					stat = curator.checkExists().forPath(consumerOffsetPath);
					if (stat == null) {
						ZKPaths.mkdirs(curator.getZookeeperClient().getZooKeeper(), consumerOffsetPath);
					}
					
					ConsumerOffset co = consumerOffsetEntry.getValue();
					curator.setData().inBackground().forPath(consumerOffsetPath, JsonUtils.marshalToByte(co));
				}
			}
		} catch (Exception e) {
			LOGGER.warn("kafka cmd offset commit err:", e);
		}
	}

	
	
	private void loadPartitionOffsetByPoolId(Map<String, TopicCfg> topicCfgMap, int poolId) {
		
		for (TopicCfg topicCfg : topicCfgMap.values()) {
			
			String topicName  = topicCfg.getName();
			String basepath = offsetManageCfg.getPath() + File.separator  + String.valueOf(poolId) + File.separator + topicName;
			
			Map<Integer, BrokerPartitionOffset> partitionOffsetMap = new ConcurrentHashMap<Integer, BrokerPartitionOffset>();
			try {
				for (BrokerPartition partition : topicCfg.getRunningOffset().getBrokerPartitions()) {
					
					String path = basepath + File.separator + partition.getPartition();
					// base node 
					createZkNode(path, null);
					byte[] data = curator.getData().forPath(path);
					
					if (isNull(data)) {
						
						BrokerPartitionOffset partitionOffset = new BrokerPartitionOffset(partition.getPartition(), 0, 0);
						partitionOffsetMap.put(partition.getPartition(), partitionOffset);
						
					} else {
						// {"logStartOffset":0,"partition":0,"producerOffset":0}
						BrokerPartitionOffset partitionOffset = JsonUtils.unmarshalFromByte(data, BrokerPartitionOffset.class);
						partitionOffsetMap.put(partition.getPartition(), partitionOffset);
						
						List<String> childrenPath = curator.getChildren().forPath(path);
						for (String clildPath : childrenPath) {
							byte[] consumerOffset = curator.getData().forPath(path + File.separator + clildPath);
							if (isNull(data)) {
								continue;
							}
							ConsumerOffset co = JsonUtils.unmarshalFromByte(consumerOffset, ConsumerOffset.class);
							partitionOffset.getConsumerOffsets().put(co.getConsumer(), co);
						}
					}
				}
				
				
				topicCfg.getRunningOffset().setPartitionOffsets( partitionOffsetMap );
				
			} catch (Exception e) {
				LOGGER.warn("", e);
			}
		}
	}
	
	public void startup() {
		
		final Map<Integer, PoolCfg> poolCfgMap = RedisEngineCtx.INSTANCE().getPoolCfgMap();
		for (Entry<Integer, PoolCfg> entry : poolCfgMap.entrySet()) {
			PoolCfg poolCfg = entry.getValue();
			if (poolCfg instanceof KafkaPoolCfg) {
				Map<String, TopicCfg> topicCfgMap = ((KafkaPoolCfg) poolCfg).getTopicCfgMap();
				
				// 加载offset
				loadPartitionOffsetByPoolId(topicCfgMap, poolCfg.getId());
			}
		}
		
		
		
		// 定时持久化offset
		executorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				try {
					// offset 数据持久化
					saveAll();
					
				} catch (Exception e) {
					LOGGER.warn("offsetAdmin err: ", e);
				}
				
			}
		}, 30, 30, TimeUnit.SECONDS);
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				close();
			}
		});

	}


	
	public void close() {
		
		// 关闭定时任务
		executorService.shutdown();
		
		// 提交本地剩余offset
		saveAll();

	}

	/**
	 * offsets 持久化
	 */
	private void saveAll() {
		final Map<Integer, PoolCfg> poolCfgMap = RedisEngineCtx.INSTANCE().getPoolCfgMap();
		for (Entry<Integer, PoolCfg> poolEntry : poolCfgMap.entrySet()) {
			PoolCfg poolCfg = poolEntry.getValue();
			if (poolCfg instanceof KafkaPoolCfg) {
				Map<String, TopicCfg> topicCfgMap = ((KafkaPoolCfg) poolCfg).getTopicCfgMap();
				
				for (Entry<String, TopicCfg> topicEntry : topicCfgMap.entrySet()) {
					TopicCfg topicCfg = topicEntry.getValue();
					savePartitionOffsets(topicCfg.getName(), topicCfg.getRunningOffset().getPartitionOffsets(), poolCfg.getId());
				}
			}
		}
	}
	
	private boolean isNull(byte[] b) {
		if (b == null) {
			return true;
		}
		
		String str = new String(b);
		if ("".equals(str) || "null".equals(str) || "NULL".equals(str))  {
			return true;
		}
		
		return false;
	}
}
