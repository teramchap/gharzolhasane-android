package com.xau.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final String PRICE_URL =
            "https://biquote.io/api/XAUUSD?allowStale=false";

    private static final String M5_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=5m&limit=200";

    private static final String M15_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=15m&limit=200";

    private static final String H1_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=1h&limit=200";

    private static final int DIR_NONE = 0;
    private static final int DIR_BUY = 1;
    private static final int DIR_SELL = -1;

    // =========================================================
    // UI
    // =========================================================

    private TextView price;
    private TextView updated;
    private TextView status;
    private TextView statusDot;

    private TextView buyQuality;
    private TextView sellQuality;

    private TextView h1Trend;
    private TextView m15Trend;
    private TextView m5Trend;
    private TextView m15Structure;
    private TextView atrValues;

    private TextView m15Break;
    private TextView pullback;

    private TextView setupState;
    private TextView setupReason;
    private TextView setupWaiting;

    private TextView bullSweep;
    private TextView bearSweep;
    private TextView bullEngulf;
    private TextView bearEngulf;
    private TextView bullBreak;
    private TextView bearBreak;

    private TextView analysis;
    private Button refresh;

    // =========================================================
    // NETWORK
    // =========================================================

    private final OkHttpClient client = new OkHttpClient();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            loadAll();
            handler.postDelayed(this, 15000);
        }
    };

    // =========================================================
    // DATA
    // =========================================================

    private final ArrayList<Candle> m5 = new ArrayList<>();
    private final ArrayList<Candle> m15 = new ArrayList<>();
    private final ArrayList<Candle> h1 = new ArrayList<>();

    private double livePrice = 0;

    // =========================================================
    // BREAKOUT STATE
    // =========================================================

    private int activeBreakDirection = DIR_NONE;
    private double activeBreakLevel = 0;
    private long activeBreakTime = 0;

    // =========================================================
    // CANDLE
    // =========================================================

    private static class Candle {

        long openTime;
        double open;
        double high;
        double low;
        double close;

        Candle(long openTime,
               double open,
               double high,
               double low,
               double close) {

            this.openTime = openTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        price = findViewById(R.id.price);
        updated = findViewById(R.id.updated);
        status = findViewById(R.id.status);
        statusDot = findViewById(R.id.statusDot);

        buyQuality = findViewById(R.id.buyQuality);
        sellQuality = findViewById(R.id.sellQuality);

        h1Trend = findViewById(R.id.h1Trend);
        m15Trend = findViewById(R.id.m15Trend);
        m5Trend = findViewById(R.id.m5Trend);
        m15Structure = findViewById(R.id.m15Structure);
        atrValues = findViewById(R.id.atrValues);

        m15Break = findViewById(R.id.m15Break);
        pullback = findViewById(R.id.pullback);

        setupState = findViewById(R.id.setupState);
        setupReason = findViewById(R.id.setupReason);
        setupWaiting = findViewById(R.id.setupWaiting);

        bullSweep = findViewById(R.id.bullSweep);
        bearSweep = findViewById(R.id.bearSweep);

        bullEngulf = findViewById(R.id.bullEngulf);
        bearEngulf = findViewById(R.id.bearEngulf);

        bullBreak = findViewById(R.id.bullBreak);
        bearBreak = findViewById(R.id.bearBreak);

        analysis = findViewById(R.id.analysis);
        refresh = findViewById(R.id.refresh);

        refresh.setOnClickListener(v -> loadAll());

        loadAll();

        handler.postDelayed(autoRefresh, 15000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        super.onDestroy();
    }

    // =========================================================
    // LOAD ALL
    // =========================================================

    private void loadAll() {

        setStatusLoading();

        new Thread(() -> {

            try {

                String priceJson = httpGet(PRICE_URL);
                double p = parsePrice(priceJson);

                String m5Json = httpGet(M5_URL);
                String m15Json = httpGet(M15_URL);
                String h1Json = httpGet(H1_URL);

                ArrayList<Candle> newM5 = parseCandles(m5Json);
                ArrayList<Candle> newM15 = parseCandles(m15Json);
                ArrayList<Candle> newH1 = parseCandles(h1Json);

                if (newM5.size() < 30 ||
                        newM15.size() < 30 ||
                        newH1.size() < 30) {

                    throw new Exception("Ø¯Ø§Ø¯Ù‡ Ú©Ø§ÙÛŒ Ø¯Ø±ÛŒØ§ÙØª Ù†Ø´Ø¯.");
                }

                runOnUiThread(() -> {

                    livePrice = p;

                    m5.clear();
                    m5.addAll(newM5);

                    m15.clear();
                    m15.addAll(newM15);

                    h1.clear();
                    h1.addAll(newH1);

                    updatePrice();

                    try {
                        calculateAnalysis();
                    } catch (Exception analysisError) {
                        showAnalysisError(analysisError);
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    status.setText("âš ï¸ DATA ERROR");
                    status.setTextColor(Color.rgb(180, 70, 70));

                    if (statusDot != null) {
                        statusDot.setText("!");
                        statusDot.setTextColor(Color.WHITE);
                        statusDot.setBackgroundColor(
                                Color.rgb(190, 70, 70)
                        );
                    }

                    setupState.setText(
                            "Ø®Ø·Ø§ Ø¯Ø± Ø¯Ø±ÛŒØ§ÙØª Ø§Ø·Ù„Ø§Ø¹Ø§Øª Ø¨Ø§Ø²Ø§Ø±"
                    );

                    setupReason.setText(
                            e.getMessage() == null
                                    ? "Ø¯Ø±ÛŒØ§ÙØª Ø¯Ø§Ø¯Ù‡ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯."
                                    : e.getMessage()
                    );

                    setupWaiting.setText(
                            "Ø§ØªØµØ§Ù„ Ø§ÛŒÙ†ØªØ±Ù†Øª Ùˆ Ù…Ù†Ø¨Ø¹ Ù‚ÛŒÙ…Øª Ø±Ø§ Ø¨Ø±Ø±Ø³ÛŒ Ú©Ù†ÛŒØ¯."
                    );
                });
            }

        }).start();
    }

    // =========================================================
    // ANALYSIS ERROR
    // =========================================================

    private void showAnalysisError(Exception e) {

        status.setText("âš ï¸ ANALYSIS ERROR");
        status.setTextColor(Color.rgb(180, 70, 70));

        if (statusDot != null) {
            statusDot.setText("!");
            statusDot.setTextColor(Color.WHITE);
            statusDot.setBackgroundColor(Color.rgb(190, 70, 70));
        }

        setupState.setText("Ø®Ø·Ø§ Ø¯Ø± ØªØ­Ù„ÛŒÙ„ Ø¯Ø§Ø¯Ù‡â€ŒÙ‡Ø§ÛŒ Ø¨Ø§Ø²Ø§Ø±");

        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        setupReason.setText(message);
        setupWaiting.setText(
                "Ø¨Ø±Ù†Ø§Ù…Ù‡ Ø¨Ø³ØªÙ‡ Ù†Ù…ÛŒâ€ŒØ´ÙˆØ¯Ø› Ø¯Ø§Ø¯Ù‡ Ø¨Ø¹Ø¯ÛŒ Ø¯Ø± Ø¨Ù‡â€ŒØ±ÙˆØ²Ø±Ø³Ø§Ù†ÛŒ Ø¨Ø¹Ø¯ÛŒ Ø¯ÙˆØ¨Ø§Ø±Ù‡ ØªØ­Ù„ÛŒÙ„ Ø®ÙˆØ§Ù‡Ø¯ Ø´Ø¯."
        );
    }

    // =========================================================
    // HTTP
    // =========================================================

    private String httpGet(String url) throws IOException {

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException(
                        "HTTP " + response.code()
                );
            }

            if (response.body() == null) {
                throw new IOException("Ù¾Ø§Ø³Ø® Ø®Ø§Ù„ÛŒ Ø§Ø³Øª.");
            }

            return response.body().string();
        }
    }

    // =========================================================
    // PRICE
    // =========================================================

    private double parsePrice(String json) throws Exception {

        JSONObject obj = new JSONObject(json);

        String[] keys = {
                "mid",
                "price",
                "last",
                "bid"
        };

        for (String key : keys) {

            if (obj.has(key) && !obj.isNull(key)) {

                Object value = obj.get(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                return Double.parseDouble(
                        String.valueOf(value)
                );
            }
        }

        if (obj.has("data") && obj.get("data") instanceof JSONObject) {

            JSONObject data = obj.getJSONObject("data");

            for (String key : keys) {

                if (data.has(key)) {

                    Object value = data.get(key);

                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }

                    return Double.parseDouble(
                            String.valueOf(value)
                    );
                }
            }
        }

        throw new Exception("Ù‚ÛŒÙ…Øª XAUUSD Ø¯Ø± Ù¾Ø§Ø³Ø® Ù¾ÛŒØ¯Ø§ Ù†Ø´Ø¯.");
    }

    // =========================================================
    // PARSE CANDLES
    // =========================================================

    private ArrayList<Candle> parseCandles(String json)
            throws Exception {

        ArrayList<Candle> result = new ArrayList<>();

        JSONArray array = null;

        String trimmed = json.trim();

        if (trimmed.startsWith("[")) {

            array = new JSONArray(trimmed);

        } else {

            JSONObject root = new JSONObject(trimmed);

            String[] possibleKeys = {
                    "data",
                    "candles",
                    "bars",
                    "ohlc",
                    "result"
            };

            for (String key : possibleKeys) {

                if (root.has(key)
                        && root.get(key) instanceof JSONArray) {

                    array = root.getJSONArray(key);
                    break;
                }
            }
        }

        if (array == null) {
            throw new Exception("Ø³Ø§Ø®ØªØ§Ø± OHLC Ù‚Ø§Ø¨Ù„ Ø´Ù†Ø§Ø³Ø§ÛŒÛŒ Ù†ÛŒØ³Øª.");
        }

        for (int i = 0; i < array.length(); i++) {

            JSONObject o = array.getJSONObject(i);

            boolean isOpen = false;

            if (o.has("isOpen")) {
                isOpen = o.optBoolean("isOpen", false);
            }

            // ÙÙ‚Ø· Ú©Ù†Ø¯Ù„ Ø¨Ø³ØªÙ‡ Ø´Ø¯Ù‡
            if (isOpen) {
                continue;
            }

            long time = getLong(o,
                    "openTime",
                    "timestamp",
                    "time",
                    "t");

            double open = getDouble(o,
                    "open",
                    "o");

            double high = getDouble(o,
                    "high",
                    "h");

            double low = getDouble(o,
                    "low",
                    "l");

            double close = getDouble(o,
                    "close",
                    "c");

            if (high <= 0 ||
                    low <= 0 ||
                    open <= 0 ||
                    close <= 0) {
                continue;
            }

            result.add(
                    new Candle(
                            time,
                            open,
                            high,
                            low,
                            close
                    )
            );
        }

        Collections.sort(
                result,
                (a, b) ->
                        Long.compare(
                                a.openTime,
                                b.openTime
                        )
        );

        return result;
    }

    private double getDouble(JSONObject o, String... keys)
            throws Exception {

        for (String key : keys) {

            if (o.has(key) && !o.isNull(key)) {

                Object value = o.get(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                return Double.parseDouble(
                        String.valueOf(value)
                );
            }
        }

        throw new Exception(
                "ÙÛŒÙ„Ø¯ OHLC Ù¾ÛŒØ¯Ø§ Ù†Ø´Ø¯."
        );
    }

    private long getLong(JSONObject o, String... keys)
            throws Exception {

        for (String key : keys) {

            if (o.has(key) && !o.isNull(key)) {

                Object value = o.get(key);

                if (value instanceof Number) {

                    long v = ((Number) value).longValue();

                    // Ø§Ú¯Ø± timestamp Ø«Ø§Ù†ÛŒÙ‡â€ŒØ§ÛŒ Ø¨Ø§Ø´Ø¯
                    if (v < 100000000000L) {
                        v *= 1000L;
                    }

                    return v;
                }

                String textValue = String.valueOf(value).trim();

                // Ù¾Ø´ØªÛŒØ¨Ø§Ù†ÛŒ Ø§Ø² timestamp Ø¹Ø¯Ø¯ÛŒ
                try {
                    long v = Long.parseLong(textValue);

                    // Ø§Ú¯Ø± timestamp Ø«Ø§Ù†ÛŒÙ‡â€ŒØ§ÛŒ Ø¨Ø§Ø´Ø¯
                    if (v < 100000000000L) {
                        v *= 1000L;
                    }

                    return v;

                } catch (NumberFormatException ignored) {
                    // Ø§Ú¯Ø± Ø¹Ø¯Ø¯ Ù†Ø¨ÙˆØ¯ØŒ ISO-8601 Ø±Ø§ Ø§Ù…ØªØ­Ø§Ù† Ù…ÛŒâ€ŒÚ©Ù†ÛŒÙ…
                }

                // Ù†Ù…ÙˆÙ†Ù‡: 2026-09-02T13:00:00Z
                try {
                    SimpleDateFormat iso =
                            new SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ssX",
                                    Locale.US
                            );

                    iso.setTimeZone(TimeZone.getTimeZone("UTC"));

                    Date date = iso.parse(textValue);

                    if (date != null) {
                        return date.getTime();
                    }

                } catch (Exception ignored) {
                    // ÙØ±Ù…Øª Ø²Ù…Ø§Ù†ÛŒ Ù†Ø§Ø´Ù†Ø§Ø®ØªÙ‡ Ø§Ø³Øª
                }

                throw new Exception(
                        "ÙØ±Ù…Øª timestamp Ù‚Ø§Ø¨Ù„ Ø´Ù†Ø§Ø³Ø§ÛŒÛŒ Ù†ÛŒØ³Øª: " + textValue
                );
            }
        }

        return 0;
    }

    // =========================================================
    // MAIN ANALYSIS
    // =========================================================

    private void calculateAnalysis() {

        if (m5.size() < 30 ||
                m15.size() < 30 ||
                h1.size() < 30) {
            return;
        }

        // ---------------------------------------------
        // TREND
        // ---------------------------------------------

        boolean h1Bull = trendBull(h1);
        boolean h1Bear = trendBear(h1);

        boolean m15Bull = trendBull(m15);
        boolean m15Bear = trendBear(m15);

        boolean m5Bull = trendBull(m5);
        boolean m5Bear = trendBear(m5);

        // ---------------------------------------------
        // STRUCTURE
        // ---------------------------------------------

        int structure = swingStructure(m15);

        boolean structureBull = structure == DIR_BUY;
        boolean structureBear = structure == DIR_SELL;

        // ---------------------------------------------
        // ATR
        // ---------------------------------------------

        double atrM5 = atr(m5, 14);
        double atrM15 = atr(m15, 14);

        // ---------------------------------------------
        // M5 TRIGGERS
        // ---------------------------------------------

        boolean bullSweep = bullishSweep(m5);
        boolean bearSweep = bearishSweep(m5);

        boolean bullEngulfing = bullishEngulfing(m5);
        boolean bearEngulfing = bearishEngulfing(m5);

        boolean bullBreakM5 = bullishBreakoutM5(m5);
        boolean bearBreakM5 = bearishBreakoutM5(m5);

        boolean bullConfirmation =
                m5Bull &&
                        (bullSweep ||
                                bullEngulfing ||
                                bullBreakM5);

        boolean bearConfirmation =
                m5Bear &&
                        (bearSweep ||
                                bearEngulfing ||
                                bearBreakM5);

        // ---------------------------------------------
        // M15 STRUCTURE BREAK
        // ---------------------------------------------

        BreakInfo breakInfo =
                detectLatestM15Break(m15);

        boolean bullStructureBreak =
                breakInfo.direction == DIR_BUY;

        boolean bearStructureBreak =
                breakInfo.direction == DIR_SELL;

        // Ø«Ø¨Øª Break ÙØ¹Ø§Ù„
        if (breakInfo.direction != DIR_NONE) {

            activeBreakDirection =
                    breakInfo.direction;

            activeBreakLevel =
                    breakInfo.level;

            activeBreakTime =
                    breakInfo.time;
        }

        // ---------------------------------------------
        // PULLBACK
        // ---------------------------------------------

        boolean buyPullback =
                activeBreakDirection == DIR_BUY
                        && activeBreakTime > 0
                        && validBullPullback(
                        m5,
                        activeBreakLevel,
                        activeBreakTime,
                        atrM5
                );

        boolean sellPullback =
                activeBreakDirection == DIR_SELL
                        && activeBreakTime > 0
                        && validBearPullback(
                        m5,
                        activeBreakLevel,
                        activeBreakTime,
                        atrM5
                );

        // ---------------------------------------------
        // QUALITY
        // ---------------------------------------------

        int buyScore = 0;
        int sellScore = 0;

        // BUY
        if (m15Bull) buyScore += 20;
        if (structureBull) buyScore += 20;
        if (m5Bull) buyScore += 15;
        if (bullStructureBreak) buyScore += 20;
        if (bullConfirmation) buyScore += 15;
        if (buyPullback) buyScore += 10;

        // SELL
        if (m15Bear) sellScore += 20;
        if (structureBear) sellScore += 20;
        if (m5Bear) sellScore += 15;
        if (bearStructureBreak) sellScore += 20;
        if (bearConfirmation) sellScore += 15;
        if (sellPullback) sellScore += 10;

        // ---------------------------------------------
        // READY
        // ---------------------------------------------

        boolean buyReady =
                m15Bull
                        && bullStructureBreak
                        && bullConfirmation
                        && buyPullback
                        && buyScore >= 75;

        boolean sellReady =
                m15Bear
                        && bearStructureBreak
                        && bearConfirmation
                        && sellPullback
                        && sellScore >= 75;

        // ---------------------------------------------
        // FORMING
        // ---------------------------------------------

        boolean buyForming =
                m15Bull &&
                        (
                                m5Bull ||
                                        structureBull ||
                                        bullStructureBreak ||
                                        bullConfirmation ||
                                        buyPullback
                        );

        boolean sellForming =
                m15Bear &&
                        (
                                m5Bear ||
                                        structureBear ||
                                        bearStructureBreak ||
                                        bearConfirmation ||
                                        sellPullback
                        );

        // ---------------------------------------------
        // UI
        // ---------------------------------------------

        updateContextUI(
                h1Bull,
                h1Bear,
                m15Bull,
                m15Bear,
                m5Bull,
                m5Bear,
                structure,
                atrM5,
                atrM15
        );

        updateBreakUI(
                bullStructureBreak,
                bearStructureBreak
        );

        updatePullbackUI(
                buyPullback,
                sellPullback
        );

        updateTriggerUI(
                bullSweep,
                bearSweep,
                bullEngulfing,
                bearEngulfing,
                bullBreakM5,
                bearBreakM5
        );

        buyQuality.setText(
                "BUY QUALITY: " +
                        buyScore +
                        " / 100"
        );

        sellQuality.setText(
                "SELL QUALITY: " +
                        sellScore +
                        " / 100"
        );

        // ---------------------------------------------
        // FINAL STATE
        // ---------------------------------------------

        if (buyReady) {

            showBuyReady(
                    buyScore,
                    atrM5
            );

        } else if (sellReady) {

            showSellReady(
                    sellScore,
                    atrM5
            );

        } else if (buyForming &&
                !sellForming) {

            showBuyForming(
                    buyScore,
                    h1Bear,
                    structureBull,
                    bullConfirmation,
                    buyPullback
            );

        } else if (sellForming &&
                !buyForming) {

            showSellForming(
                    sellScore,
                    h1Bull,
                    structureBear,
                    bearConfirmation,
                    sellPullback
            );

        } else if (buyForming &&
                sellForming) {

            showMixedMarket(
                    buyScore,
                    sellScore
            );

        } else {

            showNoSetup();
        }

        analysis.setText("");
    }

    // =========================================================
    // BUY FORMING
    // =========================================================

    private void showBuyForming(
            int score,
            boolean h1Bear,
            boolean structureBull,
            boolean confirmation,
            boolean pullbackReady) {

        status.setText(
                "BUY SETUP FORMING"
        );

        status.setTextColor(
                Color.rgb(178, 122, 0)
        );

        if (statusDot != null) {

            statusDot.setText("â—");
            statusDot.setTextColor(
                    Color.rgb(255, 244, 194)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(255, 208, 74)
            );
        }

        setupState.setText(
                "ðŸŸ¡ BUY Ø¯Ø± Ø­Ø§Ù„ Ø´Ú©Ù„â€ŒÚ¯ÛŒØ±ÛŒ Ø§Ø³Øª."
        );

        StringBuilder reason =
                new StringBuilder();

        if (h1Bear) {

            reason.append(
                    "âš ï¸ H1 Ù‡Ù†ÙˆØ² Bearish Ø§Ø³Øª.\n\n"
            );

            reason.append(
                    "Ø§Ù…Ø§ Ø§ÛŒÙ† Ù…ÙˆØ¶ÙˆØ¹ Ø¨Ù‡â€ŒØªÙ†Ù‡Ø§ÛŒÛŒ BUY Ø±Ø§ Ø¨Ø§Ø·Ù„ Ù†Ù…ÛŒâ€ŒÚ©Ù†Ø¯."
            );

        } else {

            reason.append(
                    "Ø´Ø±Ø§ÛŒØ· Context Ø¨Ø±Ø§ÛŒ BUY Ø¯Ø± Ø­Ø§Ù„ ØªÙ‚ÙˆÛŒØª Ø§Ø³Øª."
            );
        }

        setupReason.setText(
                reason.toString()
        );

        StringBuilder wait =
                new StringBuilder();

        wait.append("Ø¨Ø±Ø§ÛŒ BUY Ù…Ù†ØªØ¸Ø±:\n");

        if (!structureBull) {
            wait.append(
                    "â€¢ Ø´Ú©Ø³Øª Ù…Ø¹ØªØ¨Ø± Ø³Ø§Ø®ØªØ§Ø± M15\n"
            );
        }

        if (!confirmation) {
            wait.append(
                    "â€¢ ØªØ£ÛŒÛŒØ¯ M5\n"
            );
        }

        if (!pullbackReady) {
            wait.append(
                    "â€¢ Pullback Ø¨Ù‡ Ù†Ø§Ø­ÛŒÙ‡ Ø´Ú©Ø³Øª\n"
            );
        }

        wait.append(
                "â€¢ ØªØ£ÛŒÛŒØ¯ ÙˆØ±ÙˆØ¯ Ø¨Ø¹Ø¯ Ø§Ø² Pullback"
        );

        setupWaiting.setText(
                wait.toString()
        );
    }

    // =========================================================
    // SELL FORMING
    // =========================================================

    private void showSellForming(
            int score,
            boolean h1Bull,
            boolean structureBear,
            boolean confirmation,
            boolean pullbackReady) {

        status.setText(
                "SELL SETUP FORMING"
        );

        status.setTextColor(
                Color.rgb(180, 65, 65)
        );

        if (statusDot != null) {

            statusDot.setText("â—");
            statusDot.setTextColor(
                    Color.rgb(255, 220, 220)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(220, 80, 80)
            );
        }

        setupState.setText(
                "ðŸŸ¡ SELL Ø¯Ø± Ø­Ø§Ù„ Ø´Ú©Ù„â€ŒÚ¯ÛŒØ±ÛŒ Ø§Ø³Øª."
        );

        StringBuilder reason =
                new StringBuilder();

        if (h1Bull) {

            reason.append(
                    "âš ï¸ H1 Ù‡Ù†ÙˆØ² Bullish Ø§Ø³Øª.\n\n"
            );

            reason.append(
                    "Ø§Ù…Ø§ Ø§ÛŒÙ† Ù…ÙˆØ¶ÙˆØ¹ Ø¨Ù‡â€ŒØªÙ†Ù‡Ø§ÛŒÛŒ SELL Ø±Ø§ Ø¨Ø§Ø·Ù„ Ù†Ù…ÛŒâ€ŒÚ©Ù†Ø¯."
            );

        } else {

            reason.append(
                    "Ø´Ø±Ø§ÛŒØ· Context Ø¨Ø±Ø§ÛŒ SELL Ø¯Ø± Ø­Ø§Ù„ ØªÙ‚ÙˆÛŒØª Ø§Ø³Øª."
            );
        }

        setupReason.setText(
                reason.toString()
        );

        StringBuilder wait =
                new StringBuilder();

        wait.append("Ø¨Ø±Ø§ÛŒ SELL Ù…Ù†ØªØ¸Ø±:\n");

        if (!structureBear) {
            wait.append(
                    "â€¢ Ø´Ú©Ø³Øª Ù…Ø¹ØªØ¨Ø± Ø³Ø§Ø®ØªØ§Ø± M15\n"
            );
        }

        if (!confirmation) {
            wait.append(
                    "â€¢ ØªØ£ÛŒÛŒØ¯ M5\n"
            );
        }

        if (!pullbackReady) {
            wait.append(
                    "â€¢ Pullback Ø¨Ù‡ Ù†Ø§Ø­ÛŒÙ‡ Ø´Ú©Ø³Øª\n"
            );
        }

        wait.append(
                "â€¢ ØªØ£ÛŒÛŒØ¯ ÙˆØ±ÙˆØ¯ Ø¨Ø¹Ø¯ Ø§Ø² Pullback"
        );

        setupWaiting.setText(
                wait.toString()
        );
    }

    // =========================================================
    // BUY READY
    // =========================================================

    private void showBuyReady(
            int score,
            double atrM5) {

        status.setText("BUY READY");
        status.setTextColor(Color.rgb(20, 145, 80));

        if (statusDot != null) {
            statusDot.setText("â—");
            statusDot.setTextColor(Color.rgb(220, 255, 230));
            statusDot.setBackgroundColor(Color.rgb(40, 190, 105));
        }

        setupState.setText(
                "ðŸŸ¢ BUY READY â€” Ø´Ø±Ø§ÛŒØ· ÙˆØ±ÙˆØ¯ ØªØ£ÛŒÛŒØ¯ Ø´Ø¯Ù‡ Ø§Ø³Øª."
        );

        double entry = m5.get(m5.size() - 1).close;
        double pullbackLow = recentLow(m5, 5, 0);
        double slByPullback = pullbackLow - Math.max(atrM5 * 0.20, 0.30);
        double slByAtr = entry - atrM5 * 1.50;
        double sl = Math.min(slByPullback, slByAtr);
        double risk = entry - sl;

        if (risk <= 0) {
            risk = Math.max(atrM5, 0.30);
            sl = entry - risk;
        }

        double tp1 = entry + risk;
        double tp2 = entry + risk * 2.0;
        double tp3 = entry + risk * 3.0;

        setupReason.setText(
                "Ø´Ú©Ø³Øª Ø³Ø§Ø®ØªØ§Ø± M15 âœ“\n" +
                        "ØªØ£ÛŒÛŒØ¯ M5 âœ“\n" +
                        "Pullback âœ“\n" +
                        "ØªØ£ÛŒÛŒØ¯ ÙˆØ±ÙˆØ¯ âœ“\n\n" +
                        "ENTRY: " + fmt(entry) + "\n" +
                        "STOP LOSS: " + fmt(sl) + "\n" +
                        "TP1: " + fmt(tp1) + "\n" +
                        "TP2: " + fmt(tp2) + "\n" +
                        "TP3: " + fmt(tp3)
        );

        setupWaiting.setText(
                "BUY READY | Quality: " + score + "/100\n" +
                        "ÙˆØ±ÙˆØ¯ Ø¨Ø± Ø§Ø³Ø§Ø³ Ø¢Ø®Ø±ÛŒÙ† Ú©Ù†Ø¯Ù„ Ø¨Ø³ØªÙ‡ M5 Ø¨Ø¹Ø¯ Ø§Ø² Pullback Ù…Ø­Ø§Ø³Ø¨Ù‡ Ø´Ø¯Ù‡ Ø§Ø³Øª."
        );
    }

    private void showSellReady(
            int score,
            double atrM5) {

        status.setText("SELL READY");
        status.setTextColor(Color.rgb(190, 55, 55));

        if (statusDot != null) {
            statusDot.setText("â—");
            statusDot.setTextColor(Color.rgb(255, 225, 225));
            statusDot.setBackgroundColor(Color.rgb(215, 65, 65));
        }

        setupState.setText(
                "ðŸ”´ SELL READY â€” Ø´Ø±Ø§ÛŒØ· ÙˆØ±ÙˆØ¯ ØªØ£ÛŒÛŒØ¯ Ø´Ø¯Ù‡ Ø§Ø³Øª."
        );

        double entry = m5.get(m5.size() - 1).close;
        double pullbackHigh = recentHigh(m5, 5, 0);
        double slByPullback = pullbackHigh + Math.max(atrM5 * 0.20, 0.30);
        double slByAtr = entry + atrM5 * 1.50;
        double sl = Math.max(slByPullback, slByAtr);
        double risk = sl - entry;

        if (risk <= 0) {
            risk = Math.max(atrM5, 0.30);
            sl = entry + risk;
        }

        double tp1 = entry - risk;
        double tp2 = entry - risk * 2.0;
        double tp3 = entry - risk * 3.0;

        setupReason.setText(
                "Ø´Ú©Ø³Øª Ø³Ø§Ø®ØªØ§Ø± M15 âœ“\n" +
                        "ØªØ£ÛŒÛŒØ¯ M5 âœ“\n" +
                        "Pullback âœ“\n" +
                        "ØªØ£ÛŒÛŒØ¯ ÙˆØ±ÙˆØ¯ âœ“\n\n" +
                        "ENTRY: " + fmt(entry) + "\n" +
                        "STOP LOSS: " + fmt(sl) + "\n" +
                        "TP1: " + fmt(tp1) + "\n" +
                        "TP2: " + fmt(tp2) + "\n" +
                        "TP3: " + fmt(tp3)
        );

        setupWaiting.setText(
                "SELL READY | Quality: " + score + "/100\n" +
                        "ÙˆØ±ÙˆØ¯ Ø¨Ø± Ø§Ø³Ø§Ø³ Ø¢Ø®Ø±ÛŒÙ† Ú©Ù†Ø¯Ù„ Ø¨Ø³ØªÙ‡ M5 Ø¨Ø¹Ø¯ Ø§Ø² Pullback Ù…Ø­Ø§Ø³Ø¨Ù‡ Ø´Ø¯Ù‡ Ø§Ø³Øª."
        );
    }

    private void showMixedMarket(
            int buyScore,
            int sellScore) {

        status.setText(
                "WAIT â€” MIXED SETUP"
        );

        status.setTextColor(
                Color.rgb(160, 120, 40)
        );

        if (statusDot != null) {

            statusDot.setText("â—");
            statusDot.setTextColor(
                    Color.rgb(255, 244, 194)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(240, 190, 55)
            );
        }

        setupState.setText(
                "ðŸŸ¡ Ø¨Ø§Ø²Ø§Ø± Ø¯ÙˆØ·Ø±ÙÙ‡ Ø§Ø³ØªØ› Ù‡Ù†ÙˆØ² Setup Ø¨Ø±ØªØ± Ù…Ø´Ø®Øµ Ù†ÛŒØ³Øª."
        );

        setupReason.setText(
                "BUY QUALITY: " +
                        buyScore +
                        "\nSELL QUALITY: " +
                        sellScore
        );

        setupWaiting.setText(
                "ØªØ§ Ø²Ù…Ø§Ù†ÛŒ Ú©Ù‡ ÛŒÚ© Ø³Ù…Øª Ø´Ú©Ø³Øª Ù…Ø¹ØªØ¨Ø±ØŒ " +
                        "ØªØ£ÛŒÛŒØ¯ Ùˆ Pullback Ù…Ù†Ø§Ø³Ø¨ Ù†Ø¯Ø§Ø´ØªÙ‡ Ø¨Ø§Ø´Ø¯ØŒ Entry ØµØ§Ø¯Ø± Ù†Ù…ÛŒâ€ŒØ´ÙˆØ¯."
        );
    }

    // =========================================================
    // NO SETUP
    // =========================================================

    private void showNoSetup() {

        status.setText(
                "NO SETUP"
        );

        status.setTextColor(
                Color.rgb(100, 100, 100)
        );

        if (statusDot != null) {

            statusDot.setText("â—");
            statusDot.setTextColor(
                    Color.WHITE
            );

            statusDot.setBackgroundColor(
                    Color.rgb(150, 150, 150)
            );
        }

        setupState.setText(
                "âšª ÙØ¹Ù„Ø§Ù‹ Setup Ù…Ø¹ØªØ¨Ø±ÛŒ Ø¯Ø± Ø­Ø§Ù„ Ø´Ú©Ù„â€ŒÚ¯ÛŒØ±ÛŒ Ù†ÛŒØ³Øª."
        );

        setupReason.setText(
                "Ø´Ø±Ø§ÛŒØ· ÙØ¹Ù„ÛŒ Ø¨Ø±Ø§ÛŒ ØªØ´Ú©ÛŒÙ„ ÛŒÚ© Setup Ù…Ø´Ø®Øµ Ú©Ø§ÙÛŒ Ù†ÛŒØ³Øª."
        );

        setupWaiting.setText(
                "Ù…Ù†ØªØ¸Ø± Ø´Ú©Ù„â€ŒÚ¯ÛŒØ±ÛŒ Context Ùˆ Ø³Ø§Ø®ØªØ§Ø± Ù…Ø¹ØªØ¨Ø± Ø¨Ø§Ø²Ø§Ø± Ø¨Ø§Ø´ÛŒØ¯."
        );
    }

    // =========================================================
    // CONTEXT UI
    // =========================================================

    private void updateContextUI(
            boolean h1Bull,
            boolean h1Bear,
            boolean m15Bull,
            boolean m15Bear,
            boolean m5Bull,
            boolean m5Bear,
            int structure,
            double atrM5,
            double atrM15) {

        h1Trend.setText(
                "H1: " +
                        trendText(h1Bull, h1Bear)
        );

        m15Trend.setText(
                "M15: " +
                        trendText(m15Bull, m15Bear)
        );

        m5Trend.setText(
                "M5: " +
                        trendText(m5Bull, m5Bear)
        );

        m15Structure.setText(
                structureText(structure)
        );

        atrValues.setText(
                "ATR M5: " +
                        fmt(atrM5) +
                        "\nATR M15: " +
                        fmt(atrM15)
        );
    }

    private String trendText(
            boolean bull,
            boolean bear) {

        if (bull) {
            return "BULLISH ðŸŸ¢";
        }

        if (bear) {
            return "BEARISH ðŸ”´";
        }

        return "NEUTRAL ðŸŸ¡";
    }

    private String structureText(
            int structure) {

        if (structure == DIR_BUY) {
            return "ðŸŸ¢ Ø³Ø§Ø®ØªØ§Ø± M15: BULLISH";
        }

        if (structure == DIR_SELL) {
            return "ðŸ”´ Ø³Ø§Ø®ØªØ§Ø± M15: BEARISH";
        }

        return "ðŸŸ¡ Ø³Ø§Ø®ØªØ§Ø± M15: NEUTRAL";
    }

    // =========================================================
    // BREAK UI
    // =========================================================

    private void updateBreakUI(
            boolean bull,
            boolean bear) {

        m15Break.setText(
                "Bullish Structure Break: " +
                        (bull ? "YES âœ“" : "NO") +
                        "\n" +
                        "Bearish Structure Break: " +
                        (bear ? "YES âœ“" : "NO")
        );
    }

    // =========================================================
    // PULLBACK UI
    // =========================================================

    private void updatePullbackUI(
            boolean buy,
            boolean sell) {

        pullback.setText(
                "BUY Pullback: " +
                        (buy ? "YES âœ“" : "NO") +
                        "\n" +
                        "SELL Pullback: " +
                        (sell ? "YES âœ“" : "NO")
        );
    }

    // =========================================================
    // TRIGGER UI
    // =========================================================

    private void updateTriggerUI(
            boolean bullSweepValue,
            boolean bearSweepValue,
            boolean bullEngulfValue,
            boolean bearEngulfValue,
            boolean bullBreakValue,
            boolean bearBreakValue) {

        bullSweep.setText(
                "Liquidity Sweep ØµØ¹ÙˆØ¯ÛŒ  " +
                        (bullSweepValue ? "YES âœ“" : "NO")
        );

        bearSweep.setText(
                "Liquidity Sweep Ù†Ø²ÙˆÙ„ÛŒ  " +
                        (bearSweepValue ? "YES âœ“" : "NO")
        );

        bullEngulf.setText(
                "Bullish Engulfing  " +
                        (bullEngulfValue ? "YES âœ“" : "NO")
        );

        bearEngulf.setText(
                "Bearish Engulfing  " +
                        (bearEngulfValue ? "YES âœ“" : "NO")
        );

        bullBreak.setText(
                "Breakout ØµØ¹ÙˆØ¯ÛŒ M5  " +
                        (bullBreakValue ? "YES âœ“" : "NO")
        );

        bearBreak.setText(
                "Breakout Ù†Ø²ÙˆÙ„ÛŒ M5  " +
                        (bearBreakValue ? "YES âœ“" : "NO")
        );
    }

    // =========================================================
    // LOADING
    // =========================================================

    private void setStatusLoading() {

        status.setText(
                "â³ ANALYZING..."
        );

        status.setTextColor(
                Color.rgb(120, 120, 120)
        );

        setupState.setText(
                "Ø¯Ø± Ø­Ø§Ù„ Ø¯Ø±ÛŒØ§ÙØª Ø§Ø·Ù„Ø§Ø¹Ø§Øª Ø¨Ø§Ø²Ø§Ø±..."
        );

        setupReason.setText(
                "OHLC ØªØ§ÛŒÙ…â€ŒÙØ±ÛŒÙ…â€ŒÙ‡Ø§ÛŒ M5 / M15 / H1 Ø¯Ø± Ø­Ø§Ù„ Ø¯Ø±ÛŒØ§ÙØª Ø§Ø³Øª."
        );

        setupWaiting.setText(
                "Ù„Ø·ÙØ§Ù‹ Ú†Ù†Ø¯ Ù„Ø­Ø¸Ù‡ ØµØ¨Ø± Ú©Ù†ÛŒØ¯."
        );
    }

    // =========================================================
    // PRICE UI
    // =========================================================

    private void updatePrice() {

        price.setText(
                String.format(
                        Locale.US,
                        "%.2f",
                        livePrice
                )
        );

        SimpleDateFormat sdf =
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.US
                );

        sdf.setTimeZone(
                TimeZone.getDefault()
        );

        updated.setText(
                sdf.format(
                        new Date()
                )
        );
    }

    // =========================================================
    // TREND
    // =========================================================

    private boolean trendBull(
            ArrayList<Candle> candles) {

        double ema20 =
                ema(candles, 20);

        double ema50 =
                ema(candles, 50);

        return ema20 > ema50;
    }

    private boolean trendBear(
            ArrayList<Candle> candles) {

        double ema20 =
                ema(candles, 20);

        double ema50 =
                ema(candles, 50);

        return ema20 < ema50;
    }

    // =========================================================
    // EMA
    // =========================================================

    private double ema(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period) {
            return 0;
        }

        double multiplier =
                2.0 / (period + 1);

        double ema =
                candles
                        .get(candles.size() - period)
                        .close;

        for (int i =
             candles.size() - period + 1;
             i < candles.size();
             i++) {

            ema =
                    (candles.get(i).close - ema)
                            * multiplier
                            + ema;
        }

        return ema;
    }

    // =========================================================
    // ATR
    // =========================================================

    private double atr(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period + 1) {
            return 0;
        }

        double sum = 0;

        int start =
                candles.size() - period;

        for (int i = start; i < candles.size(); i++) {

            Candle current =
                    candles.get(i);

            Candle previous =
                    candles.get(i - 1);

            double tr =
                    Math.max(
                            current.high - current.low,
                            Math.max(
                                    Math.abs(
                                            current.high -
                                                    previous.close
                                    ),
                                    Math.abs(
                                            current.low -
                                                    previous.close
                                    )
                            )
                    );

            sum += tr;
        }

        return sum / period;
    }

    // =========================================================
    // SWING STRUCTURE
    // =========================================================

    private int swingStructure(
            ArrayList<Candle> candles) {

        if (candles.size() < 15) {
            return DIR_NONE;
        }

        List<Double> highs =
                new ArrayList<>();

        List<Double> lows =
                new ArrayList<>();

        int start =
                Math.max(
                        2,
                        candles.size() - 50
                );

        int end =
                candles.size() - 2;

        for (int i = start; i <= end; i++) {

            Candle c =
                    candles.get(i);

            if (c.high >
                    candles.get(i - 1).high &&
                    c.high >
                    candles.get(i - 2).high &&
                    c.high >
                    candles.get(i + 1).high &&
                    c.high >
                    candles.get(i + 2).high) {

                highs.add(c.high);
            }

            if (c.low <
                    candles.get(i - 1).low &&
                    c.low <
                    candles.get(i - 2).low &&
                    c.low <
                    candles.get(i + 1).low &&
                    c.low <
                    candles.get(i + 2).low) {

                lows.add(c.low);
            }
        }

        if (highs.size() < 2 ||
                lows.size() < 2) {

            return DIR_NONE;
        }

        double lastHigh =
                highs.get(highs.size() - 1);

        double previousHigh =
                highs.get(highs.size() - 2);

        double lastLow =
                lows.get(lows.size() - 1);

        double previousLow =
                lows.get(lows.size() - 2);

        boolean bullish =
                lastHigh > previousHigh &&
                        lastLow > previousLow;

        boolean bearish =
                lastHigh < previousHigh &&
                        lastLow < previousLow;

        if (bullish) {
            return DIR_BUY;
        }

        if (bearish) {
            return DIR_SELL;
        }

        return DIR_NONE;
    }

    // =========================================================
    // M5 SWEEP
    // =========================================================

    private boolean bullishSweep(
            ArrayList<Candle> candles) {

        if (candles.size() < 6) {
            return false;
        }

        Candle last =
                candles.get(candles.size() - 1);

        double previousLow =
                recentLow(
                        candles,
                        5,
                        1
                );

        return last.low < previousLow &&
                last.close > previousLow;
    }

    private boolean bearishSweep(
            ArrayList<Candle> candles) {

        if (candles.size() < 6) {
            return false;
        }

        Candle last =
                candles.get(candles.size() - 1);

        double previousHigh =
                recentHigh(
                        candles,
                        5,
                        1
                );

        return last.high > previousHigh &&
                last.close < previousHigh;
    }

    // =========================================================
    // ENGULFING
    // =========================================================

    private boolean bullishEngulfing(
            ArrayList<Candle> candles) {

        if (candles.size() < 3) {
            return false;
        }

        Candle prev =
                candles.get(
                        candles.size() - 2
                );

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        boolean prevBear =
                prev.close < prev.open;

        boolean lastBull =
                last.close > last.open;

        return prevBear &&
                lastBull &&
                last.open <= prev.close &&
                last.close >= prev.open;
    }

    private boolean bearishEngulfing(
            ArrayList<Candle> candles) {

        if (candles.size() < 3) {
            return false;
        }

        Candle prev =
                candles.get(
                        candles.size() - 2
                );

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        boolean prevBull =
                prev.close > prev.open;

        boolean lastBear =
                last.close < last.open;

        return prevBull &&
                lastBear &&
                last.open >= prev.close &&
                last.close <= prev.open;
    }

    // =========================================================
    // M5 BREAKOUT
    // =========================================================

    private boolean bullishBreakoutM5(
            ArrayList<Candle> candles) {

        if (candles.size() < 8) {
            return false;
        }

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        double level =
                previousHigh(
                        candles,
                        6,
                        1
                );

        return last.close > level;
    }

    private boolean bearishBreakoutM5(
            ArrayList<Candle> candles) {

        if (candles.size() < 8) {
            return false;
        }

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        double level =
                previousLow(
                        candles,
                        6,
                        1
                );

        return last.close < level;
    }

    // =========================================================
    // M15 BREAK INFO
    // =========================================================

    private static class BreakInfo {

        int direction;
        double level;
        long time;

        BreakInfo(
                int direction,
                double level,
                long time) {

            this.direction = direction;
            this.level = level;
            this.time = time;
        }
    }

    // =========================================================
    // M15 BREAKOUT DETECTION
    // =========================================================

    private BreakInfo detectLatestM15Break(
            ArrayList<Candle> candles) {

        if (candles.size() < 10) {

            return new BreakInfo(
                    DIR_NONE,
                    0,
                    0
            );
        }

        int last =
                candles.size() - 1;

        Candle c =
                candles.get(last);

        double highLevel =
                previousHigh(
                        candles,
                        6,
                        1
                );

        double lowLevel =
                previousLow(
                        candles,
                        6,
                        1
                );

        if (c.close > highLevel) {

            return new BreakInfo(
                    DIR_BUY,
                    highLevel,
                    c.openTime
            );
        }

        if (c.close < lowLevel) {

            return new BreakInfo(
                    DIR_SELL,
                    lowLevel,
                    c.openTime
            );
        }

        return new BreakInfo(
                DIR_NONE,
                0,
                0
        );
    }

    // =========================================================
    // PREVIOUS HIGH
    // =========================================================

    private double previousHigh(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        int end =
                candles.size() - 1 - offset;

        int start =
                Math.max(
                        0,
                        end - count + 1
                );

        double highest =
                Double.NEGATIVE_INFINITY;

        for (int i = start; i <= end; i++) {

            highest =
                    Math.max(
                            highest,
                            candles.get(i).high
                    );
        }

        return highest;
    }

    // =========================================================
    // PREVIOUS LOW
    // =========================================================

    private double previousLow(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        int end =
                candles.size() - 1 - offset;

        int start =
                Math.max(
                        0,
                        end - count + 1
                );

        double lowest =
                Double.POSITIVE_INFINITY;

        for (int i = start; i <= end; i++) {

            lowest =
                    Math.min(
                            lowest,
                            candles.get(i).low
                    );
        }

        return lowest;
    }

    // =========================================================
    // PULLBACK BUY
    // =========================================================

    private boolean validBullPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            long breakTime,
            double atrM5) {

        if (candles.size() < 3) {
            return false;
        }

        double tolerance =
                Math.max(
                        atrM5 * 0.25,
                        0.30
                );

        for (int i =
             candles.size() - 1;
             i >= 0;
             i--) {

            Candle c =
                    candles.get(i);

            // Pullback Ø¨Ø§ÛŒØ¯ Ø¨Ø¹Ø¯ Ø§Ø² Break Ø±Ø® Ø¯Ù‡Ø¯
            if (c.openTime <= breakTime) {
                continue;
            }

            boolean touched =
                    c.low <=
                            breakLevel + tolerance;

            boolean reclaimed =
                    c.close > breakLevel;

            if (touched && reclaimed) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // PULLBACK SELL
    // =========================================================

    private boolean validBearPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            long breakTime,
            double atrM5) {

        if (candles.size() < 3) {
            return false;
        }

        double tolerance =
                Math.max(
                        atrM5 * 0.25,
                        0.30
                );

        for (int i =
             candles.size() - 1;
             i >= 0;
             i--) {

            Candle c =
                    candles.get(i);

            if (c.openTime <= breakTime) {
                continue;
            }

            boolean touched =
                    c.high >=
                            breakLevel - tolerance;

            boolean rejected =
                    c.close < breakLevel;

            if (touched && rejected) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // RECENT HIGH
    // =========================================================

    private double recentHigh(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        return previousHigh(
                candles,
                count,
                offset
        );
    }

    // =========================================================
    // RECENT LOW
    // =========================================================

    private double recentLow(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        return previousLow(
                candles,
                count,
                offset
        );
    }

    // =========================================================
    // FORMAT
    // =========================================================

    private String fmt(double value) {

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }
}
