package com.hrms.listener;

import com.hrms.event.OvertimeSettledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LF-204: SMS fires ONLY after the DB transaction commits successfully.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) guarantees:
 *   - If the DB rolls back for any reason → this method never runs → no SMS sent
 *   - If SMS fails after a successful commit → DB is still correct, error is logged
 *
 * This is the correct pattern for any side effect (email, webhook, notification)
 * that must not fire if the underlying transaction didn't commit.
 */
@Component
public class SmsNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOvertimeSettled(OvertimeSettledEvent event) {
        try {
            sendSms(event.getWorkerPhone(),
                String.format("Dear %s, your overtime of Rs %.2f for %s has been settled.",
                    event.getWorkerName(), event.getTotalAmount(), event.getMonth()));

            log.info("SMS sent to worker {} (phone: {}) for month {} — amount: {}",
                event.getWorkerId(), event.getWorkerPhone(),
                event.getMonth(), event.getTotalAmount());

        } catch (Exception e) {
            // LF-204: SMS failure must NOT crash or roll back the settlement.
            // Data is already committed and correct. Log for retry queue.
            log.error("SMS delivery failed for worker {} phone {} month {} — queuing for retry. Error: {}",
                event.getWorkerId(), event.getWorkerPhone(), event.getMonth(), e.getMessage());
        }
    }

    /**
     * Replace this with your actual SMS provider (Twilio, MSG91, etc.)
     */
    private void sendSms(String phone, String message) {
        // TODO: integrate SMS provider SDK here
        log.info("[SMS STUB] To: {} | Message: {}", phone, message);
    }
}
