package com.backtester.data.model;

import com.backtester.common.model.TimeFrame;
import com.backtester.common.model.TimeSeries;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Metadata about a loaded dataset.
 */
public record DataInfo(
        String symbol,
        Path sourcePath,
        TimeFrame timeFrame,
        LocalDateTime startDate,
        LocalDateTime endDate,
        int barCount,
        double firstPrice,
        double lastPrice,
        double highestPrice,
        double lowestPrice,
        double totalVolume,
        TimeSeries.DataQuality dataQuality
) {
    /**
     * Calculate the total price change percentage
     */
    public double priceChangePercent() {
        if (firstPrice == 0) return 0;
        return ((lastPrice - firstPrice) / firstPrice) * 100;
    }

    /**
     * Calculate price range percentage
     */
    public double priceRangePercent() {
        if (lowestPrice == 0) return 0;
        return ((highestPrice - lowestPrice) / lowestPrice) * 100;
    }

    /**
     * Get duration in days
     */
    public long durationDays() {
        if (startDate == null || endDate == null) return 0;
        return java.time.Duration.between(startDate, endDate).toDays();
    }

    /**
     * Create DataInfo from a TimeSeries
     */
    public static DataInfo from(TimeSeries series, Path sourcePath) {
        if (series.isEmpty()) {
            return new DataInfo(
                    series.getSymbol(),
                    sourcePath,
                    series.getTimeFrame(),
                    null, null, 0, 0, 0, 0, 0, 0,
                    new TimeSeries.DataQuality(0, 0, 0, 0)
            );
        }

        double[] highs = series.getHighs();
        double[] lows = series.getLows();
        double[] volumes = series.getVolumes();

        double highestPrice = Double.MIN_VALUE;
        double lowestPrice = Double.MAX_VALUE;
        double totalVolume = 0;

        for (int i = 0; i < series.size(); i++) {
            highestPrice = Math.max(highestPrice, highs[i]);
            lowestPrice = Math.min(lowestPrice, lows[i]);
            totalVolume += volumes[i];
        }

        return new DataInfo(
                series.getSymbol(),
                sourcePath,
                series.getTimeFrame(),
                series.getStartDate(),
                series.getEndDate(),
                series.size(),
                series.getFirst().close(),
                series.getLast().close(),
                highestPrice,
                lowestPrice,
                totalVolume,
                series.getDataQuality()
        );
    }

    @Override
    public String toString() {
        return String.format(
                "DataInfo[%s, %s, %d bars, %s to %s, %.2f%% change]",
                symbol, timeFrame, barCount, startDate, endDate, priceChangePercent()
        );
    }
}
