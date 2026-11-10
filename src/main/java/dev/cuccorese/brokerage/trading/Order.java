package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    @Column(name = "userid")
    private Integer userId;

    private Integer size;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime datetime;

    public Money getPrice() {
        return price == null ? null : Money.of(Currencies.ARS, price);
    }
}
