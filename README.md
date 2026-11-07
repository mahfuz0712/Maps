# Maps

Sci-fi HUD styled **map app with no Google** for Android 5.0 (API 21) and newer. Built on OpenStreetMap.
Package `com.mux.android.maps` - Version `1.0.0`. Same design language as the other HUD apps.

## Features
- **Map**: pinch zoom, pan, two-finger rotate, compass button, scale bar, 7 styles (HUD night, standard, dark, light, terrain, satellite, your own tile server).
- **My location**: Android's own GPS / network location (no Play services), accuracy circle, heading arrow, follow modes (center / heading-up), GPS status page.
- **Search**: autocomplete (Photon) with Nominatim backup, type `lat, lon` to jump to coordinates, recents, saved places, Home / Work.
- **Nearby categories**: food, cafe, fuel, ATM/bank, pharmacy, hospital, hotel, shops, mosque, parking, bus stop, toilet (Overpass API), nearest first, phone / hours when OSM has them.
- **Place card**: address, distance, save, share (OpenStreetMap link), copy coordinates, call, open in another app. Long-press the map to drop a pin.
- **Directions**: car / bike / walk, up to 3 alternative routes, step list, arrival time, custom start point, swap.
- **Turn-by-turn navigation**: big maneuver banner, distance, ETA, speed, voice guidance (Android text-to-speech, English), automatic re-route, keep-screen-on, runs in the background with a notification, arrival dialog.
- **Offline**: every viewed area is cached automatically; download an area for offline use; offline-only mode; cache manager.
- **Measure distance** tool, units km / miles, `geo:` links from other apps open here.
- **SYS**: developer info, update check (GitHub releases), settings, server addresses, data credits, permissions.

## Data / servers (all free, no account, no Google)
Map data (c) OpenStreetMap contributors. Tiles: OpenStreetMap, CARTO, OpenTopoMap, Esri. Search: Photon + Nominatim.
Routing: routing.openstreetmap.de (OSRM). Places: Overpass API. The public servers are for light use - for heavy use
run your own OSRM / tile server and enter it in SYS > SERVERS / MAP.

## Not included (needs data OpenStreetMap does not provide for free)
Live traffic, public-transport routing, street view, offline search / offline routing.

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). CI: `.github/workflows/build.yml`.
Updates are read from GitHub releases of `mahfuz0712/Maps` (edit `AppInfo.kt`).
