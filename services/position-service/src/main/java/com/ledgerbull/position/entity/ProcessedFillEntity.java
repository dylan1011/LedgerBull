package com.ledgerbull.position.entity;

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
@Table(name = "processed_fills")
public class ProcessedFillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_fill_id", nullable = false, unique = true, length = 64)
    private String sourceFillId;

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

    @Column(name = "taker_side", length = 8)
    private String takerSide;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @PrePersist
    void onCreate() {
        if (ingestedAt == null) {
            ingestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceFillId() {
        return sourceFillId;
    }

    public void setSourceFillId(String sourceFillId) {
        this.sourceFillId = sourceFillId;
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

    public String getTakerSide() {
        return takerSide;
    }

    public void setTakerSide(String takerSide) {
        this.takerSide = takerSide;
    }

    public OffsetDateTime getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(OffsetDateTime ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
