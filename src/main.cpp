#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <TFT_eSPI.h>
#include <ArduinoJson.h>
#include "config.h"

// ========== DISPLAY ==========

TFT_eSPI tft = TFT_eSPI();

uint8_t animDotIndex = 0;
unsigned long lastRefresh = 0;
bool haveData = false;

// ========== DATA STRUCTS ==========

struct MinerInfo {
  String identifier;
  String algorithm;
  double hashrate;
  long accepted;
  long rejected;
  long diff;
  double sharetime;
  String software;
};

struct TxInfo {
  String datetime;
  String sender;
  String recipient;
  double amount;
};

struct ServerStats {
  String version;
  long activeConnections;
  double cpuUsage;
  double ramUsage;
};

double ducoBalance = 0.0;
double ducoPriceUSD = 0.0;     // if provided by /v2/users
MinerInfo miners[MAX_MINERS_SHOWN];
size_t minerCount = 0;

TxInfo transactions[MAX_TX_SHOWN];
size_t txCount = 0;

ServerStats serverStats;

// ========== WIFI / HTTP ==========

void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  tft.fillScreen(TFT_BLACK);
  tft.setCursor(0, 0);
  tft.setTextSize(2);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.println("Connecting WiFi...");

  uint8_t attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 40) {
    delay(250);
    tft.print(".");
    attempts++;
  }

  tft.println();
  if (WiFi.status() == WL_CONNECTED) {
    tft.setTextColor(TFT_GREEN, TFT_BLACK);
    tft.println("WiFi OK");
    tft.print("IP: ");
    tft.println(WiFi.localIP());
  } else {
    tft.setTextColor(TFT_RED, TFT_BLACK);
    tft.println("WiFi FAILED");
  }
}

bool httpGetJson(const String &url, DynamicJsonDocument &doc) {
  if (WiFi.status() != WL_CONNECTED) return false;

  WiFiClientSecure client;
  client.setInsecure();    // ignore cert validation – easiest for hobby use

  HTTPClient http;
  if (!http.begin(client, url)) {
    return false;
  }

  int code = http.GET();
  if (code != HTTP_CODE_OK) {
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  DeserializationError err = deserializeJson(doc, payload);
  if (err) {
    return false;
  }

  return true;
}

// ========== DATA FETCHING ==========

bool fetchUserData() {
  // /v2/users/<username> – balance, miners, tx, prices
  String url = String(DUCO_API_BASE) + "/v2/users/" + DUCO_USERNAME;

  // This JSON can get big; use a decent buffer
  DynamicJsonDocument doc(16384);

  if (!httpGetJson(url, doc)) {
    return false;
  }

  JsonObject result = doc["result"];
  if (result.isNull()) return false;

  // Balance
  JsonObject balanceObj = result["balance"];
  if (!balanceObj.isNull()) {
    ducoBalance = balanceObj["balance"] | 0.0;
  }

  // Optional prices (depends on API version)
  JsonObject pricesObj = result["prices"];
  if (!pricesObj.isNull()) {
    ducoPriceUSD = pricesObj["usd"] | 0.0;
  }

  // Miners
  minerCount = 0;
  JsonArray minersArr = result["miners"].as<JsonArray>();
  for (JsonObject m : minersArr) {
    if (minerCount >= MAX_MINERS_SHOWN) break;
    MinerInfo &mi = miners[minerCount++];
    mi.identifier = String((const char *)(m["identifier"] | "none"));
    mi.algorithm  = String((const char *)(m["algorithm"]  | "DUCO-S1"));
    mi.hashrate   = m["hashrate"] | 0.0;
    mi.accepted   = m["accepted"] | 0;
    mi.rejected   = m["rejected"] | 0;
    mi.diff       = m["diff"]     | 0;
    mi.sharetime  = m["sharetime"] | 0.0;
    mi.software   = String((const char *)(m["software"] | "unknown"));
  }

  // Transactions
  txCount = 0;
  JsonArray txArr = result["transactions"].as<JsonArray>();
  for (JsonObject tx : txArr) {
    if (txCount >= MAX_TX_SHOWN) break;
    TxInfo &ti = transactions[txCount++];
    ti.datetime  = String((const char *)(tx["datetime"]  | ""));
    ti.sender    = String((const char *)(tx["sender"]    | ""));
    ti.recipient = String((const char *)(tx["recipient"] | ""));
    ti.amount    = tx["amount"] | 0.0;
  }

  return true;
}

bool fetchServerStats() {
  // /statistics – same as api.json
  String url = String(DUCO_API_BASE) + "/statistics";
  DynamicJsonDocument doc(4096);

  if (!httpGetJson(url, doc)) {
    return false;
  }

  JsonObject result = doc["result"];
  if (result.isNull()) return false;

  serverStats.version           = String((const char *)(result["Server version"] | ""));
  serverStats.activeConnections = result["Active connections"] | 0;
  serverStats.cpuUsage          = result["Server CPU usage"] | 0.0;
  serverStats.ramUsage          = result["Server RAM usage"] | 0.0;

  return true;
}

// ========== DRAWING HELPERS ==========

void drawHeader() {
  tft.fillRect(0, 0, 320, 32, TFT_BLACK);
  tft.setTextSize(2);
  tft.setTextColor(TFT_YELLOW, TFT_BLACK);

  tft.setCursor(4, 4);
  tft.print("DUCO ");
  tft.setTextColor(TFT_ORANGE, TFT_BLACK);
  tft.print(DUCO_USERNAME);

  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setCursor(4, 18);
  tft.printf("Balance: %.4f", ducoBalance);

  if (ducoPriceUSD > 0.0) {
    double value = ducoBalance * ducoPriceUSD;
    tft.setCursor(190, 18);
    tft.printf("~$%.2f", value);
  }
}

void drawMinersPanel() {
  // Middle panel: roughly y = 32..150
  tft.fillRect(0, 32, 320, 120, TFT_BLACK);

  tft.setTextSize(1);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(4, 36);
  tft.print("Miners (");
  tft.print(minerCount);
  tft.print(")");

  int y = 48;
  for (size_t i = 0; i < minerCount && i < MAX_MINERS_SHOWN; i++) {
    MinerInfo &m = miners[i];

    // Identifier and hashrate
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.setCursor(4, y);
    String id = m.identifier;
    if (id.length() > 10) id = id.substring(0, 10);
    tft.print(id);
    tft.print(" ");

    tft.setTextColor(TFT_GREEN, TFT_BLACK);
    tft.printf("%.1f H/s", m.hashrate);

    // Next line: diff + accepted/rejected
    y += 10;
    tft.setCursor(8, y);
    tft.setTextColor(TFT_LIGHTGREY, TFT_BLACK);
    tft.printf("diff:%ld acc:%ld rej:%ld", m.diff, m.accepted, m.rejected);

    // Software short
    y += 10;
    tft.setCursor(8, y);
    tft.setTextColor(TFT_DARKGREY, TFT_BLACK);
    String soft = m.software;
    if (soft.length() > 26) soft = soft.substring(0, 26);
    tft.print(soft);

    y += 12;
    if (y > 140) break;
  }

  if (minerCount == 0) {
    tft.setCursor(4, 60);
    tft.setTextColor(TFT_RED, TFT_BLACK);
    tft.print("No active miners in API.");
  }
}

void drawTransactionsPanel() {
  // Bottom panel: y = 152..240
  tft.fillRect(0, 152, 320, 88, TFT_BLACK);

  tft.setTextSize(1);
  tft.setTextColor(TFT_CYAN, TFT_BLACK);
  tft.setCursor(4, 156);
  tft.print("Recent TXs");

  int y = 168;
  for (size_t i = 0; i < txCount && i < MAX_TX_SHOWN; i++) {
    TxInfo &tx = transactions[i];

    tft.setCursor(4, y);
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    String line1 = tx.sender + " -> " + tx.recipient;
    if (line1.length() > 28) line1 = line1.substring(0, 28);
    tft.print(line1);

    y += 10;
    tft.setCursor(8, y);
    tft.setTextColor(TFT_GREEN, TFT_BLACK);
    tft.printf("%.4f DUCO", tx.amount);

    y += 10;
    tft.setCursor(8, y);
    tft.setTextColor(TFT_DARKGREY, TFT_BLACK);
    String ts = tx.datetime;
    if (ts.length() > 26) ts = ts.substring(0, 26);
    tft.print(ts);

    y += 12;
    if (y > 232) break;
  }

  if (txCount == 0) {
    tft.setCursor(4, 176);
    tft.setTextColor(TFT_DARKGREY, TFT_BLACK);
    tft.print("No transactions in API.");
  }
}

void drawStatusBar() {
  // Tiny bar at bottom with server stats + animated dots
  tft.fillRect(0, 230, 320, 10, TFT_BLACK);
  tft.setTextSize(1);
  tft.setTextColor(TFT_LIGHTGREY, TFT_BLACK);
  tft.setCursor(2, 232);

  tft.printf("Srv v%s | Conn:%ld | CPU:%.0f%% RAM:%.1f%%",
             serverStats.version.c_str(),
             serverStats.activeConnections,
             serverStats.cpuUsage,
             serverStats.ramUsage);

  // Animated dots on right
  int xBase = 300;
  for (int i = 0; i < 3; i++) {
    uint16_t color = (i == animDotIndex) ? TFT_ORANGE : TFT_DARKGREY;
    tft.fillCircle(xBase + i * 5, 235, 1, color);
  }
}

// ========== HIGH-LEVEL RENDER ==========

void drawAll() {
  drawHeader();
  drawMinersPanel();
  drawTransactionsPanel();
  drawStatusBar();
}

// ========== SETUP / LOOP ==========

void setup() {
  Serial.begin(115200);

  tft.init();
  tft.setRotation(1);         // 1 = landscape (320x240)
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setTextSize(2);

  tft.setCursor(20, 40);
  tft.println("Duino CYD Dash");
  tft.setTextSize(1);
  tft.setCursor(20, 60);
  tft.println("Init...");

  connectWiFi();

  lastRefresh = 0;  // force immediate refresh
}

void loop() {
  unsigned long now = millis();

  // periodic data refresh
  if (now - lastRefresh > REFRESH_INTERVAL_MS) {
    bool userOk = fetchUserData();
    bool statsOk = fetchServerStats();

    haveData = userOk && statsOk;
    lastRefresh = now;

    if (haveData) {
      drawAll();
    } else {
      tft.fillScreen(TFT_BLACK);
      tft.setTextSize(2);
      tft.setTextColor(TFT_RED, TFT_BLACK);
      tft.setCursor(10, 40);
      tft.println("API ERROR");
      tft.setTextSize(1);
      tft.setCursor(10, 70);
      tft.println("Check WiFi / username");
    }
  }

  // Keep the status dots moving so screen feels alive even between refreshes
  static unsigned long lastAnim = 0;
  if (now - lastAnim > 300) {
    animDotIndex = (animDotIndex + 1) % 3;
    if (haveData) {
      drawStatusBar(); // re-draw small bar only
    }
    lastAnim = now;
  }

  delay(10);
}
