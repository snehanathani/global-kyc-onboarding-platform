package com.fintech.kyc.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxMessage o WHERE o.status = 'PENDING' AND o.retryCount < 5 ORDER BY o.createdAt ASC")
    List<OutboxMessage> findPendingMessagesToPublishWithLock();

    List<OutboxMessage> findByStatus(OutboxMessage.OutboxStatus status);
}
