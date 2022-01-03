package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.client.InventoryClient;
import com.ecommerce.orderservice.dto.OrderDto;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreaker;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/order")
@Slf4j
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final StreamBridge streamBridge;
    private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
    private final ExecutorService traceableExecutorService;

    @PostMapping
    public String placeOrder(@RequestBody OrderDto orderDto){

        circuitBreakerFactory.configureExecutorService(traceableExecutorService);

        Resilience4JCircuitBreaker circuitBreaker = circuitBreakerFactory.create("inventory");
        Supplier<Boolean> booleanSupplier = () -> orderDto.getOrderLineItemsList().stream()
                .allMatch(orderLineItems -> {
                    log.info("Making call to Inventory Service for SkuCode ->",orderLineItems.getSkuCode());
                    return inventoryClient.checkStock(orderLineItems.getSkuCode());
                });
        boolean isStockAvailable = circuitBreaker.run(booleanSupplier,throwable -> handleErrorCase());

       /* boolean isStockAvailable = orderDto.getOrderLineItemsList().stream()
                                .allMatch(orderLineItems -> inventoryClient.checkStock(orderLineItems.getSkuCode()));
        */


        if(isStockAvailable) {
            Order order = new Order();
            order.setOrderLineItems(orderDto.getOrderLineItemsList());
            order.setOrderNumber(UUID.randomUUID().toString());

            orderRepository.save(order);

            log.info("Sending order details with order id {} to notification service", order.getId());

            streamBridge.send("notificationEventSupplier-out-0", MessageBuilder.withPayload(order.getId()).build());

            return "Order Placed Successfully!";
        }
        else {
            return "Product not in Stock for the order!";
        }
    }

    private Boolean handleErrorCase() {
        return false;
    }
}
