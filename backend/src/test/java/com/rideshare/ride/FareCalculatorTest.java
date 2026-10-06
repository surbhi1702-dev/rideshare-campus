package com.rideshare.ride;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareCalculatorTest {

    private final FareCalculator calculator = new FareCalculator();

    @Test
    void sixHundredSplitThreeWaysIsTwoHundredEach() {
        assertThat(calculator.sharePerSeat(new BigDecimal("600"), 3)).isEqualByComparingTo("200.00");
        assertThat(calculator.splitExact(new BigDecimal("600"), List.of(1, 1, 1)))
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("200.00"), new BigDecimal("200.00"));
    }

    @Test
    void exactSplitAlwaysAddsUpToTheTotal() {
        List<BigDecimal> shares = calculator.splitExact(new BigDecimal("100.00"), List.of(1, 1, 1));

        assertThat(shares).containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test
    void studentBookingTwoSeatsPaysTwoShares() {
        List<BigDecimal> shares = calculator.splitExact(new BigDecimal("900"), List.of(1, 2));

        assertThat(shares).containsExactly(new BigDecimal("300.00"), new BigDecimal("600.00"));
    }

    @Test
    void shareIfJoinedAccountsForTheNewcomer() {
        // 2 riders on a 600 fare; a third joins -> 200 each.
        assertThat(calculator.shareIfJoined(new BigDecimal("600"), 2, 1)).isEqualByComparingTo("200.00");
        assertThat(calculator.shareIfJoined(new BigDecimal("600"), 1, 2)).isEqualByComparingTo("400.00");
    }

    @Test
    void zeroFareGivesZeroShares() {
        assertThat(calculator.splitExact(BigDecimal.ZERO, List.of(1, 1))).containsOnly(new BigDecimal("0.00"));
    }

    @Test
    void zeroSeatsIsRejected() {
        assertThatThrownBy(() -> calculator.sharePerSeat(BigDecimal.TEN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
