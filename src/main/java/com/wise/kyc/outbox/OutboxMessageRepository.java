package com.wise.kyc.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {
    
    @Query("SELECT o FROM OutboxMessage o WHERE o.status = 'PENDING' AND o.retryCount < 5 ORDER BY o.createdAt ASC")
    List<OutboxMessage> findPendingMessagesToPublish();

    List<OutboxMessage> findByStatus(OutboxMessage.OutboxStatus status);
}
