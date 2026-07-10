package com.ledgerbull.execution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "fills")
public class FillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_ref_id", nullable = false)
    private Long orderRefId;

    @Column(name = "taker_order_id", nullable = false, length = 64)
    private String takerOrderId;

    @Column(name = "maker_order_id", nullable = false, length = 64)
    private String makerOrderId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "fill_price", nullable = false)
    private Long fillPrice;

    @Column(name = "fill_quantity", nullable = false)
    private Long fillQuantity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderRefId() {
        return orderRefId;
    }

    public void setOrderRefId(Long orderRefId) {
        this.orderRefId = orderRefId;
    }

    public String getTakerOrderId() {
        return takerOrderId;
    }

    public void setTakerOrderId(String takerOrderId) {
        this.takerOrderId = takerOrderId;
    }

    public String getMakerOrderId() {
        return makerOrderId;
    }

    public void setMakerOrderId(String makerOrderId) {
        this.makerOrderId = makerOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Long getFillPrice() {
        return fillPrice;
    }

    public void setFillPrice(Long fillPrice) {
        this.fillPrice = fillPrice;
    }

    public Long getFillQuantity() {
        return fillQuantity;
    }

    public void setFillQuantity(Long fillQuantity) {
        this.fillQuantity = fillQuantity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
