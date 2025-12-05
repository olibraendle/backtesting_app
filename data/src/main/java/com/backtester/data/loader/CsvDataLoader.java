package com.backtester.data.loader;

import com.backtester.common.model.Bar;
import com.backtester.common.model.TimeFrame;
import com.backtester.common.model.TimeSeries;
import com.backtester.data.model.DataInfo;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CSV data loader for OHLCV market data.
 * Supports multiple formats:
 * - datetime,date,time,open,high,low,close,volume (datetime like "2008-12-11 01:35:00")
 * - timestamp,open,high,low,close,volume (Unix epoch)
 * - Various column naming conventions
 */
public class CsvDataLoader {

    private final CsvParserSettings settings;

    // Common datetime formats
    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    public CsvDataLoader() {
        this.settings = createDefaultSettings();
    }

    private CsvParserSettings createDefaultSettings() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(true);
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setSkipEmptyLines(true);
        settings.trimValues(true);
        settings.setMaxCharsPerColumn(100);
        settings.setMaxColumns(10);
        return settings;
    }

    /**
     * Load CSV data from a file path.
     *
     * @param path   Path to the CSV file
     * @param symbol Symbol name for the data
     * @return TimeSeries containing all loaded bars
     */
    public TimeSeries load(Path path, String symbol) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }

        List<Bar> bars = new ArrayList<>();
        TimeFrame detectedTimeFrame = TimeFrame.M5; // Default

        try (Reader reader = Files.newBufferedReader(path)) {
            CsvParser parser = new CsvParser(settings);
            parser.beginParsing(reader);

            String[] headers = parser.getContext().parsedHeaders();
            int[] columnIndices = detectColumnIndices(headers);

            String[] row;
            int index = 0;
            long previousTimestamp = 0;

            while ((row = parser.parseNext()) != null) {
                try {
                    Bar bar = parseRow(row, columnIndices, index);
                    if (bar != null && bar.isValid()) {
                        // Detect timeframe from first two bars
                        if (index == 1 && previousTimestamp > 0) {
                            long diff = bar.timestamp() - previousTimestamp;
                            detectedTimeFrame = TimeFrame.detect(diff);
                        }
                        previousTimestamp = bar.timestamp();
                        bars.add(bar);
                        index++;
                    }
                } catch (Exception e) {
                    // Skip invalid rows, log if needed
                    System.err.println("Skipping invalid row at line " + (index + 2) + ": " + e.getMessage());
                }
            }
        }

        if (bars.isEmpty()) {
            throw new IOException("No valid bars found in file: " + path);
        }

        // Sort by timestamp to ensure chronological order
        bars.sort(Bar::compareTo);

        return new TimeSeries(symbol, detectedTimeFrame, bars);
    }

    /**
     * Load CSV and return DataInfo along with TimeSeries.
     */
    public LoadResult loadWithInfo(Path path, String symbol) throws IOException {
        TimeSeries series = load(path, symbol);
        DataInfo info = DataInfo.from(series, path);
        return new LoadResult(series, info);
    }

    /**
     * Detect column indices from headers.
     * Supports various common column naming conventions.
     */
    private int[] detectColumnIndices(String[] headers) {
        int timestampIdx = -1, openIdx = -1, highIdx = -1, lowIdx = -1, closeIdx = -1, volumeIdx = -1;

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();

            if (timestampIdx < 0 && matchesAny(header, "timestamp", "time", "date", "datetime", "unix", "epoch", "t")) {
                timestampIdx = i;
            } else if (openIdx < 0 && matchesAny(header, "open", "o", "openprice", "open_price")) {
                openIdx = i;
            } else if (highIdx < 0 && matchesAny(header, "high", "h", "highprice", "high_price")) {
                highIdx = i;
            } else if (lowIdx < 0 && matchesAny(header, "low", "l", "lowprice", "low_price")) {
                lowIdx = i;
            } else if (closeIdx < 0 && matchesAny(header, "close", "c", "closeprice", "close_price", "price")) {
                closeIdx = i;
            } else if (volumeIdx < 0 && matchesAny(header, "volume", "vol", "v", "qty", "quantity")) {
                volumeIdx = i;
            }
        }

        // If headers not found, assume standard order: timestamp,open,high,low,close,volume
        if (timestampIdx < 0) timestampIdx = 0;
        if (openIdx < 0) openIdx = 1;
        if (highIdx < 0) highIdx = 2;
        if (lowIdx < 0) lowIdx = 3;
        if (closeIdx < 0) closeIdx = 4;
        if (volumeIdx < 0) volumeIdx = 5;

        return new int[]{timestampIdx, openIdx, highIdx, lowIdx, closeIdx, volumeIdx};
    }

    private boolean matchesAny(String value, String... options) {
        for (String option : options) {
            if (value.equals(option)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse a single CSV row into a Bar.
     */
    private Bar parseRow(String[] row, int[] indices, int barIndex) {
        if (row == null || row.length <= Math.max(indices[0], Math.max(indices[4], indices[5]))) {
            return null;
        }

        long timestamp = parseTimestamp(row[indices[0]]);
        double open = parseDouble(row[indices[1]]);
        double high = parseDouble(row[indices[2]]);
        double low = parseDouble(row[indices[3]]);
        double close = parseDouble(row[indices[4]]);
        double volume = indices[5] < row.length ? parseDouble(row[indices[5]]) : 0;

        return new Bar(timestamp, open, high, low, close, volume, barIndex);
    }

    /**
     * Parse timestamp - handles both Unix epoch and datetime strings.
     */
    private long parseTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Empty timestamp");
        }

        value = value.trim();

        // Try parsing as datetime string first (contains non-numeric characters like '-' or ':')
        if (value.contains("-") || value.contains("/") || value.contains("T")) {
            return parseDateTimeString(value);
        }

        // Try parsing as numeric epoch
        try {
            // Remove any decimal part
            int dotIndex = value.indexOf('.');
            if (dotIndex > 0) {
                value = value.substring(0, dotIndex);
            }

            long timestamp = Long.parseLong(value);

            // If timestamp is in seconds (less than year 2100 in seconds), convert to milliseconds
            // Year 2100 in seconds ≈ 4102444800
            if (timestamp < 4_102_444_800L) {
                timestamp *= 1000;
            }

            return timestamp;
        } catch (NumberFormatException e) {
            // Fall back to datetime parsing
            return parseDateTimeString(value);
        }
    }

    /**
     * Parse datetime string using various common formats.
     */
    private long parseDateTimeString(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(value, formatter);
                return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }
        throw new IllegalArgumentException("Unable to parse datetime: " + value);
    }

    private double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return Double.parseDouble(value.trim());
    }

    /**
     * Result of loading CSV with both data and metadata.
     */
    public record LoadResult(TimeSeries timeSeries, DataInfo dataInfo) {
    }
}
