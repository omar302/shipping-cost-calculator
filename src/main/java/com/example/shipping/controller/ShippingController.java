package com.example.shipping.controller;

import com.example.shipping.model.ShippingCost;
import com.example.shipping.model.ShippingRequest;
import com.example.shipping.service.ShippingCostService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingCostService shippingCostService;

    ShippingController(ShippingCostService shippingCostService) {
        this.shippingCostService = shippingCostService;
    }

    @PostMapping("/calculate")
    public ShippingCost calculate(@RequestBody ShippingRequest request) {
        return shippingCostService.calculate(request);
    }
}
