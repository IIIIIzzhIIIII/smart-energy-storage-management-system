package com.energy.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
@ServerEndpoint("/ws/monitor")
public class WebSocketServer {

    private static final CopyOnWriteArraySet<Session> SESSIONS = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        log.info("WebSocket 连接建立, 当前连接数: {}", SESSIONS.size());
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
        log.info("WebSocket 连接关闭, 当前连接数: {}", SESSIONS.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        SESSIONS.remove(session);
        log.error("WebSocket 连接异常", error);
    }

    /**
     * 向所有连接广播消息
     */
    public static void broadcast(String message) {
        for (Session session : SESSIONS) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("WebSocket 发送消息失败", e);
                }
            }
        }
    }
}