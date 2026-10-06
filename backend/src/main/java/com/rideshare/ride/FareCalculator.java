package com.rideshare.ride;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fare splitting in paise (integer arithmetic), so shares always add up exactly
 * to the total fare. Seats are the unit: a student booking 2 seats pays 2 shares.
 */
@Component
public class FareCalculator {

    private static final int SCALE = 2;

    /** Equal share of one seat, rounded to the paisa, e.g. 600 / 3 = 200.00. */
    public BigDecimal sharePerSeat(BigDecimal totalFare, int occupiedSeats) {
        requirePositive(occupiedSeats);
        return totalFare.divide(BigDecimal.valueOf(occupiedSeats), SCALE, RoundingMode.HALF_UP);
    }

    /** What a newcomer booking {@code seats} would pay once they are in. */
    public BigDecimal shareIfJoined(BigDecimal totalFare, int occupiedSeats, int seats) {
        requirePositive(seats);
        return totalFare.multiply(BigDecimal.valueOf(seats))
                .divide(BigDecimal.valueOf(occupiedSeats + seats), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Exact split: each seat gets floor(total / seats) paise and the remainder is
     * handed out one paisa at a time in join order.
     *
     * @param seatsInJoinOrder seats booked by each participant, in join order
     * @return share of each participant, same order, summing exactly to totalFare
     */
    public List<BigDecimal> splitExact(BigDecimal totalFare, List<Integer> seatsInJoinOrder) {
        int totalSeats = seatsInJoinOrder.stream().mapToInt(Integer::intValue).sum();
        requirePositive(totalSeats);
        long totalPaise = totalFare.setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).longValueExact();
        long basePerSeat = totalPaise / totalSeats;
        long remainder = totalPaise % totalSeats;

        List<BigDecimal> shares = new ArrayList<>(seatsInJoinOrder.size());
        for (int seats : seatsInJoinOrder) {
            long paise = basePerSeat * seats;
            long extra = Math.min(remainder, seats);
            paise += extra;
            remainder -= extra;
            shares.add(BigDecimal.valueOf(paise, SCALE));
        }
        return shares;
    }

    private static void requirePositive(int seats) {
        if (seats <= 0) {
            throw new IllegalArgumentException("Seat count must be positive");
        }
    }
}
