import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;

public class TraceConsumer {
    public static void main(String[] args) throws Exception {
        String namesrv = System.getenv().getOrDefault("NAMESRV_ADDR", "nameserver:9876");
        String topic = System.getenv().getOrDefault("TOPIC", "StudioTest");

        // enableMsgTrace=true 开启消息轨迹，轨迹 topic 为 null 时使用默认 RMQ_SYS_TRACE_TOPIC
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("studio-trace-consumer", true, null);
        consumer.setNamesrvAddr(namesrv);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            msgs.forEach(m -> System.out.println(
                "consume " + m.getMsgId() + " body=" + new String(m.getBody(), StandardCharsets.UTF_8)));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        System.out.println("consumer started, topic=" + topic);
        Thread.currentThread().join();
    }
}
