package br.com.cinq.kafka.sample.kafka;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RebalanceInProgressException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import br.com.cinq.kafka.sample.Callback;

public class BrokerConsumerClient implements Runnable, ConsumerRebalanceListener {
    Logger logger = LoggerFactory.getLogger(BrokerConsumerClient.class);

    private KafkaConsumer<String, String> consumer;

    private boolean enableAutoCommit = false;

    private Callback callback;

    private Properties properties;

    private String topic;

    private int partition;

    @Override
    public void run() {

        UUID uuid = UUID.randomUUID();
        MDC.put(getConsumer().toString(), uuid.toString());

        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Integer.MAX_VALUE);
                if (records != null) {
                    int count=0;
                    for (ConsumerRecord<String, String> record : records) {
                        logger.debug("tid {}, offset = {}, key = {}, value = {}", Thread.currentThread().getName(), record.offset(), record.key(),
                                record.value());
                        count++;
                        callback.receive(record.value());

                        BrokerConsumer.getOffsets().put(new TopicPartition(record.topic(),record.partition()), record.offset());
                    }
                    logger.info("tid {} processed {} messages",Thread.currentThread().getName(), count);
                }
                if (!isEnableAutoCommit()) {
                    try {
                        getConsumer().commitSync();
                    } catch (CommitFailedException e) {
                        // You WILL get exceptions due to rebalance, from time to time in clustered
                        // environments.
                        // It is up to you the deal with these situations.
                        // Our Callback.receive() is transactional, but you end up in situations
                        // that you will have to implement two-phase commit to recover from this failure.
                        // After you receive an error during kafka commit either rollback database transaction
                        // OR ignore kafka and seek() the offsets like we did in the example.
                        // Problem is, other nodes in the cluster WILL receive the messages you didn't
                        // commit during rebalance, so if you choose not using two phase commit and
                        // rollback eventual database transactions, you will
                        // have to deal with duplicates.
                        // Another approach is to commitSync() right after poll(). In that case, you
                        // could only update the offsets for the messages were processed. In case of commit failure,
                        // you just IGNORE the messages received, hoping that the other node in the cluster
                        // receives the messages :-o
                        logger.debug("Commit failed!!! {}", e.getMessage(),e);

                        // Just ignore Kafka, life goes on
                        for(Entry<TopicPartition, Long> entry : BrokerConsumer.getOffsets().entrySet()) {
                            consumer.seek(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (TimeoutException e) {
            logger.warn("TimeoutException", e);
        } catch (WakeupException e) {
            // In this situation it would be better to restart the loop
            logger.warn("Wake up exception", e);
        } catch(RebalanceInProgressException e) {
            logger.warn("Rebalance In Progress", e);
        } finally {
            MDC.remove(consumer.toString());
        }
    }

    /**
     * Return the current consumer. If the consumer is not registered with Kafka,
     * it will register, for All the partitions.
     * @return
     */
    public KafkaConsumer<String, String> getConsumer() {
        if (consumer == null) {
            consumer = new KafkaConsumer<>(properties);

            //List<TopicPartition> partitions = new ArrayList<TopicPartition>();
            //            for (PartitionInfo partition : consumer.partitionsFor(getTopic()))
            //                partitions.add(new TopicPartition(getTopic(), partition.partition()));
            //            consumer.assign(partitions);

            consumer.subscribe(Arrays.asList(getTopic()));

            // This will cause the
//            TopicPartition partition = new TopicPartition(getTopic(), getPartition());
//            consumer.assign(Arrays.asList(partition));

        }
        return consumer;
    }

    public void setConsumer(KafkaConsumer<String, String> consumer) {
        this.consumer = consumer;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        logger.warn("Paritions revoked from this node during rebalancing:");
        for(TopicPartition partition : partitions) {
            logger.warn("Revoked from this node {}", partition.toString());
        }
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        logger.warn("Paritions assigned to this node during rebalancing:");
        for(TopicPartition partition : partitions) {
            logger.warn("Rebalanced to this node {}", partition.toString());
        }
    }
}
