package com.example.demo.client;

import com.example.demo.model.GameAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.function.Consumer;

public class
NetworkManager {
    private StompSession session;
    private String serverUrl = "ws://localhost:8080/ws-game";
    private Consumer<GameAction> onActionReceived;
    private Consumer<String> onStatusChange;
    private Runnable onDisconnect;

    // Khởi tạo ObjectMapper để tự xử lý JSON, thay thế cho class bị deprecated
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NetworkManager(Consumer<GameAction> onActionReceived, Consumer<String> onStatusChange, Runnable onDisconnect) {
        this.onActionReceived = onActionReceived;
        this.onStatusChange = onStatusChange;
        this.onDisconnect = onDisconnect;
    }

    public void connect() {
        StandardWebSocketClient c = new StandardWebSocketClient();
        WebSocketStompClient sc = new WebSocketStompClient(c);

        // Dùng StringMessageConverter an toàn tuyệt đối cho mọi phiên bản
        sc.setMessageConverter(new StringMessageConverter());

        sc.connectAsync(serverUrl, new SessionHandler()).whenComplete((result, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> {
                    onStatusChange.accept("Lỗi: Không tìm thấy Server!");
                    onDisconnect.run();
                });
            }
        });
    }

    public void sendAction(String role, String type, String data) {
        if (session != null && session.isConnected()) {
            try {
                // Đóng gói Object thành chuỗi JSON trước khi gửi
                String jsonPayload = objectMapper.writeValueAsString(new GameAction(role, type, data));
                session.send("/app/action", jsonPayload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class SessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession s, StompHeaders h) {
            session = s;
            Platform.runLater(() -> onStatusChange.accept("Đã kết nối Server!"));
            session.subscribe("/topic/game-progress", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders h) {
                    // Nhận dữ liệu dưới dạng String
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders h, Object p) {
                    try {
                        // Giải mã chuỗi JSON ngược lại thành Object GameAction
                        GameAction action = objectMapper.readValue((String) p, GameAction.class);
                        Platform.runLater(() -> onActionReceived.accept(action));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            Platform.runLater(() -> {
                onStatusChange.accept("MẤT KẾT NỐI SERVER!");
                onDisconnect.run();
            });
        }
    }
}