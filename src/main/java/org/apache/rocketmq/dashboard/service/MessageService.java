package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;

import com.google.common.base.Function;
import org.apache.rocketmq.common.Pair;
import java.util.List;

/**
 * Message query and management service interface
 */
public interface MessageService {

    /**
     * View message detail by topic and msgId
     */
    Pair<MessageView, List<MessageTrack>> viewMessage(String topic, String msgId);

    /**
     * Query messages by topic with pagination
     */
    MessagePage queryMessageByPage(MessageQuery query);

    /**
     * Query messages by topic
     */
    List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum);

    /**
     * Query messages by topic (simplified, default maxNum)
     */
    default List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime) {
        return queryMessageByTopic(topic, beginTime, endTime, 64);
    }

    /**
     * Query messages by topic and message key
     */
    List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime);

    /**
     * Query messages by topic and key (simplified, uses default time range)
     */
    default List<MessageInfo> queryMessageByTopicAndKey(String topic, String key) {
        return queryMessageByTopicAndKey(topic, key, System.currentTimeMillis() - 7 * 24 * 3600 * 1000L, System.currentTimeMillis());
    }

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

    /**
     * Consume message directly for testing
     */
    ConsumeMessageDirectlyResult consumeMessageDirectly(String topic, String msgId, String consumerGroup, String clientId);
}