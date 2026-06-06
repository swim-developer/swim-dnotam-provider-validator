package com.github.swim_developer.validator.dnotam.provider.infrastructure.persistence;

import com.github.swim_developer.validator.dnotam.provider.domain.model.ReceivedMessage;
import com.github.swim_developer.validator.dnotam.provider.domain.port.out.ReceivedMessageRepository;
import com.github.swim_developer.validator.dnotam.provider.infrastructure.persistence.entity.ReceivedMessageEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class ReceivedMessageRepositoryImpl implements ReceivedMessageRepository, PanacheRepositoryBase<ReceivedMessageEntity, Long> {

    private final ReceivedMessageMapper mapper;

    @Inject
    public ReceivedMessageRepositoryImpl(ReceivedMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ReceivedMessage insert(ReceivedMessage domain) {
        ReceivedMessageEntity entity = mapper.toEntity(domain);
        persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public ReceivedMessage findMessageById(Long id) {
        ReceivedMessageEntity entity = findById(id);
        return entity != null ? mapper.toDomain(entity) : null;
    }

    @Override
    public List<ReceivedMessage> findBySubscriptionId(String subscriptionId) {
        return list("subscriptionId = ?1 order by receivedAt desc", subscriptionId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findByQueueName(String queueName) {
        return list("queueName = ?1 order by receivedAt desc", queueName)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countBySubscriptionId(String subscriptionId) {
        return count("subscriptionId", subscriptionId);
    }

    @Override
    public List<ReceivedMessage> findBySubscriptionIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return list("subscriptionId in ?1 order by receivedAt desc", ids)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findBySubscriptionIdsAfter(List<String> ids, LocalDateTime after) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return list("subscriptionId in ?1 and receivedAt >= ?2 order by receivedAt desc", ids, after)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findBySubscriptionIdsFiltered(List<String> ids, boolean xmlOnly) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String query = xmlOnly
                ? "subscriptionId in ?1 and lower(contentType) like ?2 order by receivedAt desc"
                : "subscriptionId in ?1 and lower(contentType) not like ?2 order by receivedAt desc";
        return list(query, ids, "%xml%").stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findBySubscriptionIdsAfterFiltered(List<String> ids, LocalDateTime after, boolean xmlOnly) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String query = xmlOnly
                ? "subscriptionId in ?1 and receivedAt >= ?2 and lower(contentType) like ?3 order by receivedAt desc"
                : "subscriptionId in ?1 and receivedAt >= ?2 and lower(contentType) not like ?3 order by receivedAt desc";
        return list(query, ids, after, "%xml%").stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findRecentMessages() {
        return list("FROM ReceivedMessageEntity ORDER BY receivedAt DESC")
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ReceivedMessage> findMessagesAfter(LocalDateTime after) {
        return list("receivedAt >= ?1 order by receivedAt desc", after)
                .stream().map(mapper::toDomain).toList();
    }
}
