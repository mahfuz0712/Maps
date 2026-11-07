package com.mux.android.maps.map

import com.mux.android.maps.data.AppSettings
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

class MapStyle(val key: String, val label: String, val note: String, val invert: Boolean, val make: () -> ITileSource)

/** Tiles from a user supplied server, e.g. https://tiles.example.com/{z}/{x}/{y}.png */
private class TemplateSource(private val tpl: String) :
    OnlineTileSourceBase("CustomTiles", 0, 20, 256, ".png", arrayOf(tpl)) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        tpl.replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
            .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
}

/** Esri tiles use z/y/x order. */
private class EsriSource : OnlineTileSourceBase(
    "EsriImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Tiles © Esri - Source: Esri, Maxar, Earthstar Geographics"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

object Styles {
    private const val OSM = "© OpenStreetMap contributors"

    val all = listOf(
        MapStyle("hud", "HUD NIGHT", "OpenStreetMap, inverted to match the HUD theme", true) { TileSourceFactory.MAPNIK },
        MapStyle("osm", "STANDARD", "OpenStreetMap standard map", false) { TileSourceFactory.MAPNIK },
        MapStyle("dark", "DARK", "CARTO Dark Matter", false) {
            XYTileSource(
                "CartoDark", 0, 20, 256, ".png",
                arrayOf("https://a.basemaps.cartocdn.com/dark_all/", "https://b.basemaps.cartocdn.com/dark_all/", "https://c.basemaps.cartocdn.com/dark_all/"),
                "$OSM © CARTO"
            )
        },
        MapStyle("light", "LIGHT", "CARTO Positron", false) {
            XYTileSource(
                "CartoLight", 0, 20, 256, ".png",
                arrayOf("https://a.basemaps.cartocdn.com/light_all/", "https://b.basemaps.cartocdn.com/light_all/", "https://c.basemaps.cartocdn.com/light_all/"),
                "$OSM © CARTO"
            )
        },
        MapStyle("topo", "TERRAIN", "OpenTopoMap: contours and hills", false) {
            XYTileSource(
                "OpenTopo", 0, 17, 256, ".png",
                arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
                "$OSM, SRTM | © OpenTopoMap (CC-BY-SA)"
            )
        },
        MapStyle("sat", "SATELLITE", "Esri World Imagery", false) { EsriSource() },
        MapStyle("custom", "CUSTOM SERVER", "Your own tile server (set it in SYS)", false) {
            val t = AppSettings.customTiles.trim()
            if (t.contains("{z}") && t.contains("{x}") && t.contains("{y}")) TemplateSource(t) else TileSourceFactory.MAPNIK
        }
    )

    fun byKey(k: String): MapStyle = all.firstOrNull { it.key == k } ?: all[0]
}
