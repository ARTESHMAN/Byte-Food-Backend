
package org.croissantbuddies.snappfood.dto;

import org.croissantbuddies.snappfood.entity.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class OrderDTO {

    public Long id;
    public String delivery_address;
    public Long customer_id;
    public Long vendor_id;
    public Long coupon_id;
    public List<Long> item_ids;
    public BigDecimal raw_price;
    public BigDecimal tax_fee;
    public BigDecimal additional_fee;
    public BigDecimal courier_fee;
    public BigDecimal pay_price;
    public Long courier_id;
    public String status;
    public String created_at;
    public String updated_at;

    public OrderDTO(Order order) {
        this.id = order.getId();
        this.delivery_address = order.getDeliveryAddress();
        this.customer_id = order.getCustomerId();
        this.vendor_id = order.getVendorId();
        this.coupon_id = order.getCouponId();

        if (order.getItems() != null) {
            this.item_ids = order.getItems().stream()
                    .map(cartItem -> cartItem.getFood().getId())
                    .collect(Collectors.toList());
        }
        this.raw_price = BigDecimal.valueOf(order.getRawPrice());
        this.tax_fee = BigDecimal.valueOf(order.getTaxFee());
        this.additional_fee = BigDecimal.valueOf(order.getAdditionalFee());
        this.courier_fee = BigDecimal.valueOf(order.getCourierFee());
        this.pay_price = BigDecimal.valueOf(order.getPayPrice());
        this.courier_id = order.getCourierId();
        this.status = order.getStatus().name().toLowerCase();
        this.created_at = order.getCreatedAt().toString();
        this.updated_at = order.getUpdatedAt().toString();
    }
}