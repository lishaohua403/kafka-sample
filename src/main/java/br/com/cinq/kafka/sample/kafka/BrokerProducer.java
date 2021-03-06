package br.com.cinq.kafka.sample.kafka;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.cinq.kafka.sample.Producer;
import br.com.cinq.kafka.sample.exception.QueueException;

/**
 * Implements the producer for Kafka.
 */
@Component
@Profile("!unit")
@Qualifier("sampleProducer")
public class BrokerProducer implements Producer {
	Logger logger = LoggerFactory.getLogger(Producer.class);

	/** Concurrent threads reading messages */
	@Value("${broker.partitions:5}")
	private int partitions;

	/** Topic for subscribe, if applicable */
	@Value("${broker.topic:karma-sample}")
	private String topic;

	/** Kafka server */
	@Value("${broker.producer.bootstrapServer:localhost:9092}")
	private String bootstrapServer;

	/** Buffer messages, prior to send. This will only have effect if you turn linger.ms on, 
	 * Kafka will group messages by lingering and filling batch size, whatever comes first.
	 * By activating linger, you will limit calls to Kafka, and that may put a limit on
	 * your producer. The acks also will have an effect. If the ack takes more than linger.ms,
	 * then you won´t have any benefit. 
	 * Size of the package for sending messages */
	@Value("${broker.producer.batch-size:16384}")
	private int batchSize;

	/** Time to wait before sending */
	@Value("${broker.producer.linger-time-ms:0}")
	private int lingerTime;

	/** Amount of memory available for buffering, before block the producer */
	@Value("${broker.producer.buffer-size:33554432}")
	private int bufferMemory;

	/** Zookeeper server, to manage topics and partitions */
	@Value("${broker.zookeeper:localhost:2181}")
	private String zookeeper;

	/** Call Future<>.get() or not */
	@Value("${broker.producer.async-calls:false}")
	private boolean asyncCalls = false;


	/** acks all - wait to kafka replicate the message to all replicas. 
	 * 1-wait the leader receive the update. 0-no acks.
	 */
	@Value("${broker.producer.acks:all}")
	private String acks;

	/** Instance of the producer */
	private KafkaProducer<String, String> producer = null;

	private KafkaProducer<String, String> getProducer() {
		if (producer == null) {

			logger.info("Connecting to {}", getBootstrapServer());

			Properties props = new Properties();
			props.put("bootstrap.servers", getBootstrapServer());
			props.put("acks", getAcks());
			props.put("retries", 0);
			props.put("batch.size", getBatchSize());
			props.put("linger.ms", getLingerTime());
			props.put("buffer.memory", getBufferMemory());
			props.put("key.serializer", StringSerializer.class.getName());
			props.put("value.serializer", StringSerializer.class.getName());

			// For partitioner, not a valid setting for kafka
			// this is faster then checking at Cluster
			// Kafka already have a default partitioner
			//props.put("partitioner.class", BrokerProducerPartitioner.class.getName());
			//props.put("num.partitions", getPartitions());

			producer = new KafkaProducer<>(props);

		}
		return producer;
	}

	public String getBootstrapServer() {
		return bootstrapServer;
	}

	public void setBootstrapServer(String bootstrapServer) {
		this.bootstrapServer = bootstrapServer;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public int getLingerTime() {
		return lingerTime;
	}

	public void setLingerTime(int lingerTime) {
		this.lingerTime = lingerTime;
	}

	public int getBufferMemory() {
		return bufferMemory;
	}

	public void setBufferMemory(int bufferMemory) {
		this.bufferMemory = bufferMemory;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}

	public int getPartitions() {
		return partitions;
	}

	public void setPartitions(int partitions) {
		this.partitions = partitions;
	}

	public String getZookeeper() {
		return zookeeper;
	}

	public void setZookeeper(String zookeeper) {
		this.zookeeper = zookeeper;
	}

	/**
    * Send the message. The message must be serialized as string
    */
    @Override
    public void send(String message) throws QueueException {
        producer = getProducer();
        
		try {
			logger.debug("Sending message {} to [{}]", getTopic(), message);

			Future<RecordMetadata> future = producer.send(new ProducerRecord<String, String>(getTopic(), message),new Callback() {

				@Override
				public void onCompletion(RecordMetadata metadata, Exception exception) {
					logger.info("Message delivered - offset {}", metadata.offset(), exception);
				}
			});

			if (!isAsyncCalls()) {
				future.get();
			}
		} catch (InterruptedException | ExecutionException e) {
			logger.warn("Kafka Producer [{}]", e.getMessage(), e);
		}
	}

	public boolean isAsyncCalls() {
		return asyncCalls;
	}

	public void setAsyncCalls(boolean asyncCalls) {
		this.asyncCalls = asyncCalls;
	}

	public String getAcks() {
		return acks;
	}

	public void setAcks(String acks) {
		this.acks = acks;
	}
}
