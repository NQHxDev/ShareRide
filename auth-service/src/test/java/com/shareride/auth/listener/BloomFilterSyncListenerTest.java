package com.shareride.auth.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.shareride.auth.event.AccountRegisteredEvent;
import com.shareride.auth.service.EmailBloomFilterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for BloomFilterSyncListener verifying @TransactionalEventListener correctly calls
 * addEmail() after transaction commit.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilterSyncListener Unit Tests")
class BloomFilterSyncListenerTest {

    @Mock private EmailBloomFilterService emailBloomFilterService;

    @InjectMocks private BloomFilterSyncListener listener;

    @Test
    @DisplayName("onAccountRegistered calls addEmail with event email")
    void onAccountRegistered_callsAddEmail() {
        AccountRegisteredEvent event = new AccountRegisteredEvent("newuser@example.com");

        listener.onAccountRegistered(event);

        verify(emailBloomFilterService, times(1)).addEmail(eq("newuser@example.com"));
    }

    @Test
    @DisplayName("onAccountRegistered handles normalized email")
    void onAccountRegistered_normalizedEmail() {
        AccountRegisteredEvent event = new AccountRegisteredEvent("  MixedCase@Example.COM  ");

        listener.onAccountRegistered(event);

        verify(emailBloomFilterService, times(1)).addEmail(eq("  MixedCase@Example.COM  "));
    }

    @Test
    @DisplayName("onAccountRegistered does not propagate exceptions")
    void onAccountRegistered_doesNotPropagateExceptions() {
        AccountRegisteredEvent event = new AccountRegisteredEvent("error@example.com");
        doThrow(new RuntimeException("Valkey down"))
                .when(emailBloomFilterService)
                .addEmail(anyString());

        listener.onAccountRegistered(event);

        verify(emailBloomFilterService, times(1)).addEmail(eq("error@example.com"));
    }

    @Test
    @DisplayName("onAccountRegistered invoked for multiple events")
    void onAccountRegistered_multipleEvents() {
        AccountRegisteredEvent event1 = new AccountRegisteredEvent("user1@example.com");
        AccountRegisteredEvent event2 = new AccountRegisteredEvent("user2@example.com");

        listener.onAccountRegistered(event1);
        listener.onAccountRegistered(event2);

        verify(emailBloomFilterService, times(1)).addEmail(eq("user1@example.com"));
        verify(emailBloomFilterService, times(1)).addEmail(eq("user2@example.com"));
    }
}
