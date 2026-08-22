package com.vanter.emberagent;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Thin wrapper around Spring's {@code WebSocketStompClient} pointed at the isolated
 * {@code /ws/print-agent} endpoint (spec §2.6). Reconnection with backoff is driven by
 * {@link Main} calling {@link #connect} again after a STOMP session ends — kept out of this
 * class to keep it a single, testable responsibility (open one session, subscribe, hand
 * messages to a callback).
 */
public class AgentConnection {

    public interface PrintJobHandler extends Consumer<PrintJobPayload> {}

    public record PrintJobPayload(String jobId, String role, String payload) {}

    public StompSession connect(String wsBaseUrl, String jwt, String agentId, PrintJobHandler handler)
            throws Exception {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.afterPropertiesSet();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(taskScheduler);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        StompSession session = stompClient
                .connectAsync(wsBaseUrl + "/ws/print-agent", (WebSocketHttpHeaders) null, connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get();

        session.subscribe("/topic/print-agent/" + agentId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return PrintJobPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                handler.accept((PrintJobPayload) payload);
            }
        });

        return session;
    }
}
