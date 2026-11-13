package com.shareride.auth.listener;

import com.shareride.auth.event.AccountRegisteredEvent;
import com.shareride.auth.service.EmailBloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for AccountRegisteredEvent and synchronizes email to Bloom filter. Listener executes
 * AFTER transaction commits to ensure email is persisted before adding to filter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterSyncListener {

    private final EmailBloomFilterService emailBloomFilterService;

    /**
     * Handles account registration event by adding email to Bloom filter. Executes asynchronously
     * after PostgreSQL transaction commits successfully.
     *
     * @param event the account registered event containing email
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountRegistered(AccountRegisteredEvent event) {
        try {
            emailBloomFilterService.addEmail(event.getEmail());
            log.debug(
                    "Email added to Bloom filter after account registration: {}", event.getEmail());

        } catch (Exception e) {
            log.error("Failed to add email to Bloom filter: {}", event.getEmail(), e);
        }
    }
}
