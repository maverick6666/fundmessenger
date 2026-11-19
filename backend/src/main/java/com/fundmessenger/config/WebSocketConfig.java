package com.fundmessenger.config;

import com.fundmessenger.auth.service.JwtService;
import com.fundmessenger.chat.repository.ChatMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final ChatMemberRepository chatMemberRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final Pattern CHAT_TOPIC_PATTERN = Pattern.compile("^/topic/chat/(\\d+)$");

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontendUrl);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor == null) {
                    return message;
                }

                // ── CONNECT: JWT 인증 ──
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            Long userId = jwtService.getUserId(token);
                            accessor.setUser(new StompPrincipal(userId.toString()));
                            log.debug("WebSocket 연결: userId={}", userId);
                        } catch (Exception e) {
                            log.warn("WebSocket JWT 인증 실패: {}", e.getMessage());
                        }
                    }
                }

                // ── SUBSCRIBE: 구독 레벨 인가 ──
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    Principal user = accessor.getUser();

                    if (destination != null) {
                        // /user/queue/** — 사용자 전용 큐 (알림 등), 허용
                        // /topic/chat/{roomId} — 채팅방 멤버만 구독 허용
                        Matcher chatMatcher = CHAT_TOPIC_PATTERN.matcher(destination);
                        if (chatMatcher.matches()) {
                            if (user == null) {
                                log.warn("WebSocket 구독 거부: 미인증 사용자, destination={}", destination);
                                throw new IllegalArgumentException("인증되지 않은 사용자는 채팅방을 구독할 수 없습니다.");
                            }
                            Long roomId = Long.parseLong(chatMatcher.group(1));
                            Long userId = Long.parseLong(user.getName());

                            boolean isMember = chatMemberRepository.existsByRoomIdAndUserIdActive(roomId, userId);
                            if (!isMember) {
                                log.warn("WebSocket 구독 거부: userId={}, roomId={} — 멤버 아님", userId, roomId);
                                throw new IllegalArgumentException("해당 채팅방의 멤버가 아닙니다.");
                            }
                            log.debug("WebSocket 구독 허용: userId={}, roomId={}", userId, roomId);
                        }
                        // 기타 /topic/** 및 /user/queue/** — 기본 허용
                    }
                }

                return message;
            }
        });
    }

    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
