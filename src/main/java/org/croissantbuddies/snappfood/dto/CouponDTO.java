package org.croissantbuddies.snappfood.dto;

import java.time.LocalDate;

public class CouponDTO {
    public Long id;
    public String coupon_code;
    public String type;
    public double value;
    public double min_price;
    public int user_count;
    public LocalDate start_date;
    public LocalDate end_date;
}