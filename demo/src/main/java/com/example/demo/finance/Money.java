package com.example.demo.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    public static BigDecimal of(Number value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(SCALE, ROUNDING);
        }
        return BigDecimal.valueOf(value.doubleValue()).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, ROUNDING);
    }

    public static Double percentChange(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.abs(previous)), 2, ROUNDING)
                .doubleValue();
    }
}
