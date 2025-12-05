package com.backtester.common.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

/**
 * An immutable time series of OHLCV bars.
 * Provides efficient access to historical price data and various helper methods.
 */
public class TimeSeries {
    private final String symbol;
    private final TimeFrame timeFrame;
    private final List<Bar> bars;
    private final Map<Long, Integer> timestampIndex;

    // Cached arrays for performance
    private double[] closes;
    private double[] opens;
    private double[] highs;
    private double[] lows;
    private double[] volumes;

    public TimeSeries(String symbol, TimeFrame timeFrame, List<Bar> bars) {
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.timeFrame = timeFrame != null ? timeFrame : TimeFrame.UNKNOWN;

        // Re-index bars and store as immutable list
        List<Bar> indexedBars = new ArrayList<>(bars.size());
        this.timestampIndex = new HashMap<>(bars.size());

        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i).withIndex(i);
            indexedBars.add(bar);
            timestampIndex.put(bar.timestamp(), i);
        }

        this.bars = Collections.unmodifiableList(indexedBars);
    }

    /**
     * Convenience constructor that auto-detects timeframe (or uses UNKNOWN).
     */
    public TimeSeries(String symbol, List<Bar> bars) {
        this(symbol, detectTimeFrame(bars), bars);
    }

    private static TimeFrame detectTimeFrame(List<Bar> bars) {
        if (bars == null || bars.size() < 2) {
            return TimeFrame.UNKNOWN;
        }
        long interval = bars.get(1).timestamp() - bars.get(0).timestamp();
        return TimeFrame.fromMilliseconds(interval);
    }

    // ===== Basic Accessors =====

    public String getSymbol() {
        return symbol;
    }

    public TimeFrame getTimeFrame() {
        return timeFrame;
    }

    public int size() {
        return bars.size();
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }

    public Bar get(int index) {
        if (index < 0 || index >= bars.size()) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + bars.size());
        }
        return bars.get(index);
    }

    public Bar getFirst() {
        if (bars.isEmpty()) {
            throw new NoSuchElementException("TimeSeries is empty");
        }
        return bars.get(0);
    }

    public Bar getLast() {
        if (bars.isEmpty()) {
            throw new NoSuchElementException("TimeSeries is empty");
        }
        return bars.get(bars.size() - 1);
    }

    public Optional<Bar> getBarAtTime(long timestamp) {
        Integer index = timestampIndex.get(timestamp);
        return index != null ? Optional.of(bars.get(index)) : Optional.empty();
    }

    public List<Bar> getBars() {
        return bars;
    }

    public Stream<Bar> stream() {
        return bars.stream();
    }

    // ===== Date Range Methods =====

    public LocalDateTime getStartDate() {
        return bars.isEmpty() ? null : bars.get(0).getDateTime();
    }

    public LocalDateTime getEndDate() {
        return bars.isEmpty() ? null : bars.get(bars.size() - 1).getDateTime();
    }

    public LocalDateTime getStartDate(ZoneId zone) {
        return bars.isEmpty() ? null : bars.get(0).getDateTime(zone);
    }

    public LocalDateTime getEndDate(ZoneId zone) {
        return bars.isEmpty() ? null : bars.get(bars.size() - 1).getDateTime(zone);
    }

    // ===== Price Array Accessors (with lazy initialization) =====

    public double[] getCloses() {
        if (closes == null) {
            closes = bars.stream().mapToDouble(Bar::close).toArray();
        }
        return closes.clone();
    }

    public double[] getOpens() {
        if (opens == null) {
            opens = bars.stream().mapToDouble(Bar::open).toArray();
        }
        return opens.clone();
    }

    public double[] getHighs() {
        if (highs == null) {
            highs = bars.stream().mapToDouble(Bar::high).toArray();
        }
        return highs.clone();
    }

    public double[] getLows() {
        if (lows == null) {
            lows = bars.stream().mapToDouble(Bar::low).toArray();
        }
        return lows.clone();
    }

    public double[] getVolumes() {
        if (volumes == null) {
            volumes = bars.stream().mapToDouble(Bar::volume).toArray();
        }
        return volumes.clone();
    }

    // ===== Single Value Accessors =====

    public double getClose(int index) {
        return get(index).close();
    }

    public double getOpen(int index) {
        return get(index).open();
    }

    public double getHigh(int index) {
        return get(index).high();
    }

    public double getLow(int index) {
        return get(index).low();
    }

    public double getVolume(int index) {
        return get(index).volume();
    }

    // ===== Slicing Methods =====

    public TimeSeries slice(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > bars.size() || fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    String.format("Invalid slice range [%d, %d) for size %d", fromIndex, toIndex, bars.size()));
        }
        return new TimeSeries(symbol, timeFrame, bars.subList(fromIndex, toIndex));
    }

    public TimeSeries sliceFromStart(int count) {
        return slice(0, Math.min(count, bars.size()));
    }

    public TimeSeries sliceFromEnd(int count) {
        int start = Math.max(0, bars.size() - count);
        return slice(start, bars.size());
    }

    public TimeSeries sliceByTime(long startTimestamp, long endTimestamp) {
        List<Bar> filtered = bars.stream()
                .filter(b -> b.timestamp() >= startTimestamp && b.timestamp() <= endTimestamp)
                .toList();
        return new TimeSeries(symbol, timeFrame, filtered);
    }

    // ===== Statistical Helpers =====

    public double getHighestHigh(int lookback, int endIndex) {
        int start = Math.max(0, endIndex - lookback + 1);
        double highest = Double.MIN_VALUE;
        for (int i = start; i <= endIndex && i < bars.size(); i++) {
            highest = Math.max(highest, bars.get(i).high());
        }
        return highest;
    }

    public double getLowestLow(int lookback, int endIndex) {
        int start = Math.max(0, endIndex - lookback + 1);
        double lowest = Double.MAX_VALUE;
        for (int i = start; i <= endIndex && i < bars.size(); i++) {
            lowest = Math.min(lowest, bars.get(i).low());
        }
        return lowest;
    }

    public double getHighestClose(int lookback, int endIndex) {
        int start = Math.max(0, endIndex - lookback + 1);
        double highest = Double.MIN_VALUE;
        for (int i = start; i <= endIndex && i < bars.size(); i++) {
            highest = Math.max(highest, bars.get(i).close());
        }
        return highest;
    }

    public double getLowestClose(int lookback, int endIndex) {
        int start = Math.max(0, endIndex - lookback + 1);
        double lowest = Double.MAX_VALUE;
        for (int i = start; i <= endIndex && i < bars.size(); i++) {
            lowest = Math.min(lowest, bars.get(i).close());
        }
        return lowest;
    }

    // ===== Data Quality =====

    public DataQuality getDataQuality() {
        if (bars.isEmpty()) {
            return new DataQuality(0, 0, 0, 0);
        }

        int invalidBars = 0;
        int gaps = 0;
        long expectedInterval = timeFrame.getMilliseconds();

        for (int i = 0; i < bars.size(); i++) {
            if (!bars.get(i).isValid()) {
                invalidBars++;
            }
            if (i > 0) {
                long actualInterval = bars.get(i).timestamp() - bars.get(i - 1).timestamp();
                // Allow 10% tolerance for gaps
                if (actualInterval > expectedInterval * 1.5) {
                    gaps++;
                }
            }
        }

        double coverage = bars.isEmpty() ? 0 :
                (double) (bars.size() - gaps) / bars.size() * 100;

        return new DataQuality(bars.size(), invalidBars, gaps, coverage);
    }

    public record DataQuality(int totalBars, int invalidBars, int gaps, double coveragePercent) {
        public boolean isGood() {
            return invalidBars == 0 && coveragePercent >= 95;
        }
    }

    @Override
    public String toString() {
        return String.format("TimeSeries[%s, %s, %d bars, %s to %s]",
                symbol, timeFrame, bars.size(), getStartDate(), getEndDate());
    }
}
