#include <Arduino.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ILI9341.h>

#ifndef TFT_CS
#define TFT_CS 15
#endif

#ifndef TFT_DC
#define TFT_DC 2
#endif

#ifndef TFT_RST
#define TFT_RST -1
#endif

#ifndef TFT_BL
#define TFT_BL 21
#endif

#ifndef TFT_MOSI
#define TFT_MOSI 13
#endif

#ifndef TFT_MISO
#define TFT_MISO 12
#endif

#ifndef TFT_SCLK
#define TFT_SCLK 14
#endif

SPIClass hspi(HSPI);
Adafruit_ILI9341 tft = Adafruit_ILI9341(&hspi, TFT_CS, TFT_DC, TFT_RST);

void setup() {
  pinMode(TFT_BL, OUTPUT);
  digitalWrite(TFT_BL, HIGH);      // turn on backlight

  Serial.begin(115200);

  // Init HSPI with CYD pins
  hspi.begin(TFT_SCLK, TFT_MISO, TFT_MOSI, TFT_CS);

  tft.begin();
  tft.setRotation(1);              // landscape (320x240)

  // Test pattern
  tft.fillScreen(ILI9341_RED);
  delay(800);
  tft.fillScreen(ILI9341_GREEN);
  delay(800);
  tft.fillScreen(ILI9341_BLUE);
  delay(800);
  tft.fillScreen(ILI9341_BLACK);

  tft.setTextSize(2);
  tft.setTextColor(ILI9341_YELLOW, ILI9341_BLACK);
  tft.setCursor(10, 40);
  tft.println("HELLO CYD");
  tft.setCursor(10, 70);
  tft.println("If you see this,");
  tft.setCursor(10, 90);
  tft.println("display works.");
}

void loop() {
  // simple heartbeat: invert screen colors every second
  static bool invert = false;
  static uint32_t last = 0;
  uint32_t now = millis();

  if (now - last > 1000) {
    invert = !invert;
    if (invert) {
      tft.invertDisplay(true);
    } else {
      tft.invertDisplay(false);
    }
    last = now;
  }
}
