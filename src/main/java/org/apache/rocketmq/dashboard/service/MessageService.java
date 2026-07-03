package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.MessageInfo;

import java.util.List;

/**
 * Message query and management service interface
 */
public interface MessageService {

    /**
     * Query messages by topic
     */
    List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum);

    /**
     * Query messages by topic and message key
     */
    List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime);

    /**
     * Query messages by consumer group
     */
    List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime);

    /**
     * Get message by ID
     */
    MessageInfo getMessageById(String msgId);

    /**
     * Get messages by offset
     */
    List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count);

    /**
     * Search offset by timestamp
     */
    long searchOffset(String topic, String brokerName, int queueId, long timestamp);

    /**
     * Get maximum offset
     */
    long getMaxOffset(String topic, String brokerName, int queueId);

    /**
     * Get minimum offset
     */
    long getMinOffset(String topic, String brokerName, int queueId);

    /**
     * Delete message
     */
    boolean deleteMessage(String topic, String msgId);

    /**
     * Resend message to new topic
     */
    boolean resendMessage(String msgId, String newTopic);
}