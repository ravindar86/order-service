package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.model.OrderLineItems;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    private List<OrderLineItems> orderLineItemsList;
}
