package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock MessageChannel channel;
    @InjectMocks JwtChannelInterceptor interceptor;

    private UserDetails waiter() {
        return new User("waiter@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_WAITER")));
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_withValidJwt_setsAuthentication() {
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractSubject("valid-token")).thenReturn("waiter@test.com");
        when(userDetailsService.loadUserByUsername("waiter@test.com")).thenReturn(waiter());

        Message<?> result = interceptor.preSend(connectMessage("Bearer valid-token"), channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(resultAccessor.getUser().getName()).isEqualTo("waiter@test.com");
    }

    @Test
    void connect_withNoAuthHeader_passesThrough() {
        Message<?> result = interceptor.preSend(connectMessage(null), channel);

        assertThat(result).isNotNull();
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void connect_withInvalidJwt_passesThrough() {
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        Message<?> result = interceptor.preSend(connectMessage("Bearer bad-token"), channel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNull();
    }

    @Test
    void nonConnectFrame_isNotProcessed() {
        Message<?> result = interceptor.preSend(subscribeMessage(), channel);

        assertThat(result).isNotNull();
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.any());
    }
}
