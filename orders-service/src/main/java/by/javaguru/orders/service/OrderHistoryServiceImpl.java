package by.javaguru.orders.service;

import by.javaguru.core.types.OrderStatus;
import by.javaguru.orders.dao.jpa.entity.OrderHistoryEntity;
import by.javaguru.orders.dao.jpa.repository.OrderHistoryRepository;
import by.javaguru.orders.dto.OrderHistory;
import by.javaguru.orders.dto.OrderHistoryResponse;
import by.javaguru.orders.saga.SagaState;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static by.javaguru.orders.saga.OrderSagaTopology.SAGA_STATE_STORE;

@Service
public class OrderHistoryServiceImpl implements OrderHistoryService {
    private final OrderHistoryRepository orderHistoryRepository;
    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    public OrderHistoryServiceImpl(OrderHistoryRepository orderHistoryRepository, StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    @Override
    public void add(UUID orderId, OrderStatus orderStatus) {
        OrderHistoryEntity entity = new OrderHistoryEntity();
        entity.setOrderId(orderId);
        entity.setStatus(orderStatus);
        entity.setCreatedAt(new Timestamp(new Date().getTime()));
        orderHistoryRepository.save(entity);
    }

    @Override
    public List<OrderHistoryResponse> findByOrderId(UUID orderId) {
        KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

        ReadOnlyKeyValueStore<UUID, SagaState> sagaStore =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(
                        SAGA_STATE_STORE,
                        QueryableStoreTypes.keyValueStore()
                ));

        SagaState sagaState = sagaStore.get(orderId);
        OrderHistory orderHistory = new OrderHistory(
            null, // id is not set in the original code
            orderId,
            sagaState.getStatus(),
            null // createdAt is not set in the original code
        );

        return List.of(new OrderHistoryResponse(
                orderHistory.id(),
                orderHistory.orderId(),
                orderHistory.status(),
                orderHistory.createdAt()
        ));
    }
}
