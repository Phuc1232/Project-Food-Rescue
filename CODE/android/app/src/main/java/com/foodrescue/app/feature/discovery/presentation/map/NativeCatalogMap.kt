package com.foodrescue.app.feature.discovery.presentation.map

import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.core.common.GeoPoint

import android.content.Context
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.createBitmap
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

internal enum class MapLoadState { LOADING, READY, ERROR }
internal data class StoreMapPin(val id: Int, val location: PickupLocation, val priceLabel: String)

@Composable
internal fun NativeCatalogMap(
    offers: List<StoreMapPin>, selectedId: Int?, fitRequest: Int, retryRequest: Int,
    topInset: Int, bottomInset: Int, viewport: List<Double>,
    onSelect: (Int) -> Unit, onViewport: (List<Double>) -> Unit, onLoad: (MapLoadState) -> Unit,
    modifier: Modifier = Modifier, customerPoint: GeoPoint? = null, locateRequest: Int = 0,
    sidePanelInset: Int = 0,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val select = rememberUpdatedState(onSelect)
    val camera = rememberUpdatedState(onViewport)
    val load = rememberUpdatedState(onLoad)
    val controller = remember(context) {
        StoreMapController(context, viewport, { select.value(it) }, { camera.value(it) }, { load.value(it) })
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> controller.lifecycle(event) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.destroy()
        }
    }
    AndroidView(factory = { controller.view }, modifier = modifier,
        update = { controller.showPosition(customerPoint); controller.update(offers, selectedId, fitRequest, retryRequest, topInset, bottomInset, sidePanelInset); controller.locate(locateRequest) })
}

/** Owns the native renderer; composable filters never recreate the MapView. */
private class StoreMapController(
    private val context: Context,
    private val initialViewport: List<Double>,
    private val onSelect: (Int) -> Unit,
    private val onViewport: (List<Double>) -> Unit,
    private val onLoad: (MapLoadState) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private fun px(dp: Int) = (dp * density).roundToInt()
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var offers = emptyList<StoreMapPin>()
    private var customerPoint: GeoPoint? = null
    private var locateRequest = 0
    private var selectedId: Int? = null
    private var fitRequest = 0
    private var retryRequest = 0
    private var topInset = px(150)
    private var bottomInset = px(270)
    private var leftInset = 0
    private var positioned = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private val imageKeys = mutableSetOf<String>()
    val view: MapView
    private val memoryCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() { if (!destroyed) view.onLowMemory() }
    }

    init {
        MapLibre.getInstance(context)
        view = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).logoEnabled(false).attributionEnabled(true)
            .foregroundLoadColor(Color.rgb(239, 242, 237)))
        view.contentDescription = "Bản đồ cửa hàng. Kéo để di chuyển, dùng hai ngón tay để phóng to hoặc thu nhỏ."
        view.onCreate(null)
        context.registerComponentCallbacks(memoryCallbacks)
        view.addOnDidFinishLoadingMapListener { if (!destroyed) onLoad(MapLoadState.READY) }
        view.addOnDidFailLoadingMapListener { if (!destroyed) onLoad(MapLoadState.ERROR) }
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            ready.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                setAttributionGravity(Gravity.BOTTOM or Gravity.START)
                setAttributionMargins(leftInset + px(12), px(8), px(12), bottomInset + px(10))
            }
            ready.setMinZoomPreference(9.0)
            ready.setMaxZoomPreference(19.0)
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(10.777, 106.699)).zoom(14.0).build()
            ready.addOnMapClickListener { location ->
                val point = ready.projection.toScreenLocation(location)
                val touch = RectF(point.x - px(10), point.y - px(10), point.x + px(10), point.y + px(10))
                val feature = ready.queryRenderedFeatures(touch, SELECTED_PIN, PINS, DOTS).firstOrNull()
                if (feature != null && feature.hasProperty("boxId")) {
                    onSelect(feature.getNumberProperty("boxId").toInt())
                    true
                } else false
            }
            ready.addOnCameraIdleListener {
                if (!destroyed && positioned) ready.cameraPosition.let { camera ->
                    camera.target?.let { onViewport(listOf(it.latitude, it.longitude, camera.zoom, if (leftInset > 0) 1.0 else 0.0)) }
                }
            }
            loadStyle()
        }
    }

    fun update(next: List<StoreMapPin>, selection: Int?, fit: Int, retry: Int, top: Int, bottom: Int, side: Int) {
        if (destroyed) return
        val offersChanged = offers != next
        val selectionChanged = selectedId != selection
        val mustFit = fit != fitRequest || offersChanged
        offers = next
        selectedId = selection
        fitRequest = fit
        leftInset = side
        topInset = if (side > 0) px(12) else top
        bottomInset = if (side > 0) px(74) else bottom
        map?.uiSettings?.setAttributionMargins(leftInset + px(12), px(8), px(12), bottomInset + px(10))
        if (retry != retryRequest) {
            retryRequest = retry
            loadStyle()
            return
        }
        if (style == null) return
        if (offersChanged || selectionChanged) updatePins()
        if (mustFit) fitAll() else if (selectionChanged) focusSelection()
    }

    private fun loadStyle() {
        val ready = map ?: return
        style = null
        imageKeys.clear()
        onLoad(MapLoadState.LOADING)
        ready.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/positron")) { loaded ->
            if (destroyed) return@setStyle
            style = loaded
            loaded.getLayer("water")?.setProperties(fillColor("#D7E8E3"))
            loaded.getLayer("park")?.setProperties(fillColor("#E1EBD9"))
            loaded.getLayer("label_city")?.setProperties(visibility(Property.NONE))
            loaded.getLayer("label_city_capital")?.setProperties(visibility(Property.NONE))
            loaded.layers.filterIsInstance<SymbolLayer>().filter {
                it.id.startsWith("highway-name") || it.id.startsWith("label_") || it.id.startsWith("water_name")
            }.forEach { layer ->
                layer.setProperties(textField(Expression.coalesce(
                    Expression.get("name:vi"), Expression.get("name"), Expression.get("name:latin"), Expression.get("name_en"))))
            }
            loaded.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
            loaded.addSource(GeoJsonSource("customer-position", FeatureCollection.fromFeatures(emptyArray<Feature>())))
            loaded.addLayer(CircleLayer("customer-position-dot", "customer-position").withProperties(
                circleRadius(7f), circleColor(Color.rgb(37, 99, 235)), circleStrokeWidth(3f), circleStrokeColor(Color.WHITE)))
            updatePosition()
            loaded.addLayer(CircleLayer(DOTS, SOURCE).withProperties(
                circleRadius(5f), circleColor(Color.rgb(0, 102, 94)), circleStrokeWidth(2f), circleStrokeColor(Color.WHITE)))
            loaded.addLayer(SymbolLayer(PINS, SOURCE).withProperties(
                iconImage(Expression.get("pin")), iconAnchor("bottom"),
                iconAllowOverlap(false), iconIgnorePlacement(false), iconPadding(4f)))
            loaded.addLayer(SymbolLayer(SELECTED_PIN, SOURCE).withProperties(
                iconImage(Expression.get("pin")), iconAnchor("bottom"), iconAllowOverlap(true), iconIgnorePlacement(true)))
            ready.uiSettings.setAttributionMargins(leftInset + px(12), px(8), px(12), bottomInset + px(10))
            updatePins()
            view.post {
                if (destroyed) return@post
                val sameLayout = initialViewport.getOrNull(3) == if (leftInset > 0) 1.0 else 0.0
                if (!positioned && initialViewport.size >= 3 && sameLayout) {
                    ready.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(initialViewport[0], initialViewport[1]), initialViewport[2]))
                    positioned = true
                } else fitAll()
            }
        }
    }

    private fun updatePins() {
        val loaded = style ?: return
        val features = offers.mapNotNull { box ->
            val location = box.location
            val chosen = box.id == selectedId
            val key = "price-${box.id}-${box.priceLabel}-$chosen"
            val label = box.priceLabel
            if (imageKeys.add(key)) loaded.addImage(key, pricePin(label, chosen))
            Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
                addNumberProperty("boxId", box.id)
                addNumberProperty("order", if (chosen) 1 else 0)
                addStringProperty("pin", key)
            }
        }
        loaded.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        loaded.getLayerAs<SymbolLayer>(PINS)?.setFilter(Expression.neq(Expression.get("boxId"), Expression.literal(selectedId ?: -1)))
        loaded.getLayerAs<SymbolLayer>(SELECTED_PIN)?.setFilter(Expression.eq(Expression.get("boxId"), Expression.literal(selectedId ?: -1)))
    }

    fun showPosition(point: GeoPoint?) {
        if (customerPoint != point) { customerPoint = point; updatePosition() }
    }
    private fun updatePosition() {
        val features = customerPoint?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) } ?: emptyList()
        style?.getSourceAs<GeoJsonSource>("customer-position")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    fun locate(request: Int) {
        if (request == locateRequest) return
        locateRequest = request
        val point = customerPoint ?: return
        val bounds = LatLngBounds.Builder()
            .include(LatLng(point.latitude + .002, point.longitude + .002))
            .include(LatLng(point.latitude - .002, point.longitude - .002)).build()
        move(CameraUpdateFactory.newLatLngBounds(bounds, leftInset + px(50), topInset + px(40), px(50), bottomInset + px(42)))
    }

    private fun fitAll() {
        val ready = map ?: return
        if (view.width == 0 || view.height == 0) return
        val points = offers.mapNotNull { it.location?.let { point -> LatLng(point.latitude, point.longitude) } }
        val bounds = LatLngBounds.Builder()
        if (points.isEmpty()) {
            move(CameraUpdateFactory.newLatLngZoom(LatLng(10.777, 106.699), 13.5))
        } else {
            points.forEach(bounds::include)
            // A single point needs nonzero bounds, and every pin needs room for its price label.
            if (points.size == 1) {
                bounds.include(LatLng(points[0].latitude + .0016, points[0].longitude + .0016))
                bounds.include(LatLng(points[0].latitude - .0016, points[0].longitude - .0016))
            }
            move(CameraUpdateFactory.newLatLngBounds(bounds.build(), leftInset + px(58), topInset + px(45), px(58), bottomInset + px(50)))
        }
        positioned = true
    }

    private fun focusSelection() {
        val point = offers.find { it.id == selectedId }?.location ?: return
        // Reuse the visible map area so the selected pin cannot disappear under the store card.
        val bounds = LatLngBounds.Builder()
            .include(LatLng(point.latitude + .002, point.longitude + .002))
            .include(LatLng(point.latitude - .002, point.longitude - .002)).build()
        move(CameraUpdateFactory.newLatLngBounds(bounds, leftInset + px(50), topInset + px(40), px(50), bottomInset + px(42)))
    }

    private fun move(update: org.maplibre.android.camera.CameraUpdate) {
        val ready = map ?: return
        val animate = positioned && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (animate) ready.easeCamera(update, 450) else ready.moveCamera(update)
    }

    private fun pricePin(label: String, selected: Boolean): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val width = (paint.measureText(label) + px(26)).roundToInt()
        val height = px(32)
        val bitmap = createBitmap(width + px(8), height + px(12))
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)
        val rect = RectF(px(4).toFloat(), px(3).toFloat(), (width + px(4)).toFloat(), height.toFloat())
        paint.color = if (selected) Color.rgb(0, 102, 94) else Color.WHITE
        paint.setShadowLayer(px(3).toFloat(), 0f, px(2).toFloat(), 0x26002620)
        canvas.drawRoundRect(rect, px(20).toFloat(), px(20).toFloat(), paint)
        paint.clearShadowLayer()
        val center = bitmap.width / 2f
        canvas.drawPath(Path().apply {
            moveTo(center - px(6), height - px(1).toFloat())
            lineTo(center, height + px(7).toFloat())
            lineTo(center + px(6), height - px(1).toFloat()); close()
        }, paint)
        paint.color = if (selected) Color.WHITE else Color.rgb(0, 77, 70)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, center, rect.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
        return bitmap
    }

    fun lifecycle(event: Lifecycle.Event) {
        if (destroyed) return
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
            Lifecycle.Event.ON_PAUSE -> if (resumed) { view.onPause(); resumed = false }
            Lifecycle.Event.ON_STOP -> if (started) { view.onStop(); started = false }
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    fun destroy() {
        if (destroyed) return
        if (resumed) view.onPause()
        if (started) view.onStop()
        destroyed = true
        context.unregisterComponentCallbacks(memoryCallbacks)
        view.onDestroy()
    }

    companion object {
        private const val SOURCE = "foodrescue-offers"
        private const val PINS = "foodrescue-price-pins"
        private const val SELECTED_PIN = "foodrescue-selected-pin"
        private const val DOTS = "foodrescue-store-dots"
    }
}
