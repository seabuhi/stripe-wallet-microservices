package com.backend.stripewalletapi.service;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * KafkaProducerService: Real-world distributed messaging service.
 */
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> sendAsync(String topic, String key, String payload) {
        log.debug("KAFKA: Sending message to topic [{}] with key [{}]", topic, key);
        
        String traceId = MDC.get("traceId");
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        
        if (traceId != null) {
            record.headers().add("traceId", traceId.getBytes());
        }

        return kafkaTemplate.send(record);
    }
}
