package by.javaguru.orders.service;

import by.javaguru.orders.dto.OrderHistoryResponse;
import by.javaguru.orders.saga.SagaState;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static by.javaguru.orders.saga.SagaEventsProcessor.SAGA_STATE_STORE;

@RequiredArgsConstructor
@Service
public class OrderHistoryServiceImpl implements OrderHistoryService {
    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    @Override
    public List<OrderHistoryResponse> findByOrderId(UUID orderId) {
        KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

        ReadOnlyKeyValueStore<UUID, SagaState> sagaStore =
                kafkaStreams.store(StoreQueryParameters.fromNameAndType(
                        SAGA_STATE_STORE,
                        QueryableStoreTypes.keyValueStore()
                ));

        SagaState sagaState = sagaStore.get(orderId);

        return List.of(new OrderHistoryResponse(
                null,
                sagaState.orderId(),
                sagaState.status(),
                null
        ));
    }
}
