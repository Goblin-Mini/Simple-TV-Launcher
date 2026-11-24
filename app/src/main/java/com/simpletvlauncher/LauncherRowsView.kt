package com.simpletvlauncher

import android.animation.ValueAnimator
import java.io.BufferedInputStream
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Android TV home screen.
 *
 * A fixed identity column sits to the left of each publisher's programme shelf.
 * Programme artwork keeps its real aspect ratio: landscape banners stay wide,
 * portrait posters stay tall. Labels are outside the artwork, on the background.
 * DPAD focus expands the active shelf and compresses the shelf immediately above it.
 */
private object LauncherPosterQueue {
    // Posters are loaded independently from catalogue/weather work. No request is discarded.
    val executor = ThreadPoolExecutor(
        3, 3, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        { task -> Thread(task, "launcher-poster").apply { isDaemon = true } }
    )
}

private object LauncherPosterCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(uri: String): Bitmap? = cache.get(uri)
    fun put(uri: String, bitmap: Bitmap) = cache.put(uri, bitmap)
}

class LauncherRowsView(
    context: Context,
    private val host: MainActivity,
    private val prefs: Prefs
) : LinearLayout(context) {

    private data class PosterCard(
        val root: LinearLayout,
        val image: ImageView,
        val title: TextView,
        var aspectRatio: Float = 1.45f
    )

    private data class Shelf(
        val channel: TvChannels.Channel,
        val row: LinearLayout,
        val identity: LinearLayout,
        val icon: ImageView,
        val appName: TextView,
        val channelName: TextView?,
        val cards: LinearLayout,
        val posterCards: MutableList<PosterCard> = ArrayList(),
        var animator: ValueAnimator? = null,
        var artHeight: Int = 188,
        var captionHeight: Int = 52,
        var rowHeight: Int = 278,
        var iconSize: Int = 76
    )

    private data class ShelfMetrics(
        val rowHeight: Int,
        val artHeight: Int,
        val captionHeight: Int,
        val iconSize: Int,
        val appNameSp: Float,
        val channelNameSp: Float,
        val captionSp: Float
    )

    private val scale = UI.scale(context)
    private val appRow = LinearLayout(context)
    private val appRail = LinearLayout(context)
    private val channelContainer = LinearLayout(context)

    private var desiredHeight = 0
    private var appsSignature = ""
    private var channelsSignature = ""
    private var appsBuilt = false
    private var channelsBuilt = false
    private var allAppsButton: View? = null
    private var emptyChannelsRow: View? = null
    private var channelModels: List<TvChannels.Channel> = emptyList()
    private val shelves = LinkedHashMap<String, Shelf>()
    private var focusedChannelKey: String? = null

    private val identityWidth = (184 * scale).roundToInt()
    private val rowGap = (30 * scale).roundToInt()

    private val normal = ShelfMetrics(278, 188, 52, 76, 19f, 13f, 16f)
    private val expanded = ShelfMetrics(348, 238, 58, 92, 22f, 15f, 17f)
    private val compact = ShelfMetrics(210, 132, 42, 58, 16f, 11f, 14f)

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(
            (42 * scale).roundToInt(), (10 * scale).roundToInt(),
            (48 * scale).roundToInt(), (42 * scale).roundToInt()
        )

        appRail.orientation = HORIZONTAL
        appRail.clipChildren = false
        appRail.clipToPadding = false
        appRow.orientation = HORIZONTAL
        appRow.gravity = Gravity.CENTER_VERTICAL
        appRow.clipChildren = false
        appRow.clipToPadding = false

        val appsSide = buildAppsSidePanel()
        appRow.addView(appsSide, LayoutParams(identityWidth, (194 * scale).roundToInt()))

        val appScroll = horizontalRail()
        appScroll.addView(appRail, FrameLayout.LayoutParams(-2, -1))
        appRow.addView(appScroll, LayoutParams(0, (194 * scale).roundToInt(), 1f))
        addView(appRow, LayoutParams(-1, (208 * scale).roundToInt()))

        channelContainer.orientation = VERTICAL
        channelContainer.clipChildren = false
        channelContainer.clipToPadding = false
        addView(channelContainer, LayoutParams(-1, -2))
        updateDesiredHeight()
    }

    fun contentHeight(): Int = desiredHeight

    fun render(apps: List<AppRepo.AppItem>, channels: List<TvChannels.Channel>) {
        val focusedTag = findFocus()?.tag as? String
        val nextAppsSignature = apps.joinToString("|") { it.pkg + ":" + it.cls + ":" + it.label }
        val nextChannelsSignature = channels.joinToString("|") { it.key }

        if (!appsBuilt || nextAppsSignature != appsSignature) {
            buildAppRail(apps)
            appsSignature = nextAppsSignature
            appsBuilt = true
        }
        if (!channelsBuilt || nextChannelsSignature != channelsSignature) {
            buildChannelRails(channels)
            channelsSignature = nextChannelsSignature
            channelsBuilt = true
        }
        updateDesiredHeight()
        requestLayout()

        post {
            val target = if (!focusedTag.isNullOrBlank()) findViewWithTag<View>(focusedTag) else null
            if (target != null && target.isFocusable && !target.hasFocus()) target.requestFocus()
        }
    }

    fun focusFirst() {
        focusChannelShelf(null)
        allAppsButton?.let { button ->
            button.post { if (button.isAttachedToWindow) button.requestFocus() }
        }
    }

    fun focusAppAt(index: Int) {
        focusChannelShelf(null)
        val target = appRail.getChildAt(index)
        if (target != null && target.isFocusable) target.requestFocus() else focusFirst()
    }

    fun needsChannelPrograms(channel: TvChannels.Channel): Boolean =
        !shelves.containsKey(channel.key)

    private fun buildAppsSidePanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            tag = "all_apps"
            setOnClickListener { host.openAllAppsPage() }
            setOnFocusChangeListener { view, focused ->
                view.alpha = if (focused) 1f else .9f
                if (focused) {
                    focusChannelShelf(null)
                    host.ensureKotlinFocusVisible(view)
                }
            }
        }

        val icon = TextView(context).apply {
            text = "▦"
            textSize = 46f * scale
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE53935.toInt())
            }
            isFocusable = false
        }
        panel.addView(icon, LayoutParams((88 * scale).roundToInt(), (88 * scale).roundToInt()))

        panel.addView(TextView(context).apply {
            text = "Приложения"
            textSize = 20f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = false
        }, LayoutParams(-1, (48 * scale).roundToInt()))

        allAppsButton = panel
        return panel
    }

    private fun buildAppRail(apps: List<AppRepo.AppItem>) {
        appRail.removeAllViews()
        apps.forEach { item ->
            val tile = MainActivity.AppTile(context, item, prefs.themeColor()).apply {
                tag = "app:" + item.pkg
                radius = 18f * scale
                isFocusable = true
                setOnClickListener { host.launchFromKotlin(item) }
                setOnFocusChangeListener { view, focused ->
                    if (focused) {
                        focusChannelShelf(null)
                        host.onKotlinAppFocused(item.label)
                        host.ensureKotlinFocusVisible(view)
                    }
                }
                setOnLongClickListener {
                    host.openKotlinAppMenu(this)
                    true
                }
            }
            appRail.addView(tile, LayoutParams((276 * scale).roundToInt(), (164 * scale).roundToInt()).apply {
                rightMargin = (16 * scale).roundToInt()
                topMargin = (8 * scale).roundToInt()
                bottomMargin = (8 * scale).roundToInt()
            })
        }
    }

    private fun buildChannelRails(channels: List<TvChannels.Channel>) {
        val wanted = channels.map { it.key }.toSet()
        val focusedTag = findFocus()?.tag as? String

        val removed = shelves.keys.filter { it !in wanted }
        removed.forEach { key ->
            shelves.remove(key)?.let { channelContainer.removeView(it.row) }
        }
        channelModels = channels

        // Reorder existing shelves without detaching their focused cards.
        channels.forEach { channel ->
            shelves[channel.key]?.let { shelf ->
                val current = channelContainer.indexOfChild(shelf.row)
                val desired = channels.takeWhile { it.key != channel.key }
                    .count { shelves.containsKey(it.key) }
                if (current >= 0 && current != desired) {
                    channelContainer.removeView(shelf.row)
                    channelContainer.addView(shelf.row, desired)
                }
            }
        }
        updateEmptyChannelsRow()
        updateDesiredHeight()

        if (!focusedTag.isNullOrBlank()) {
            post {
                val target = findViewWithTag<View>(focusedTag)
                if (target != null && target.isFocusable && !target.hasFocus()) target.requestFocus()
            }
        }
    }

    private fun updateEmptyChannelsRow() {
        if (shelves.isNotEmpty()) {
            emptyChannelsRow?.let { channelContainer.removeView(it) }
            emptyChannelsRow = null
            return
        }

        if (emptyChannelsRow == null) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
            }

            val identity = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                addView(TextView(context).apply {
                    text = "▤"
                    textSize = 40f * scale
                    setTextColor(0xFFE7E7E7.toInt())
                    gravity = Gravity.CENTER
                }, LayoutParams((78 * scale).roundToInt(), (78 * scale).roundToInt()))
                addView(TextView(context).apply {
                    text = "Каналы"
                    textSize = 19f * scale
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }, LayoutParams(-1, (42 * scale).roundToInt()))
            }
            row.addView(identity, LayoutParams(identityWidth, (210 * scale).roundToInt()))

            row.addView(TextView(context).apply {
                text = if (channelModels.isEmpty()) "Выбрать каналы приложений  ›"
                else "Загружаю выбранные каналы…"
                textSize = 20f * scale
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = channelModels.isEmpty()
                isClickable = channelModels.isEmpty()
                if (channelModels.isEmpty()) {
                    setOnClickListener { host.openChannelSettingsFromKotlin() }
                }
            }, LayoutParams(0, (56 * scale).roundToInt(), 1f))

            channelContainer.addView(row, LayoutParams(-1, (210 * scale).roundToInt()).apply {
                bottomMargin = rowGap
            })
            emptyChannelsRow = row
        } else {
            val action = (emptyChannelsRow as? ViewGroup)?.getChildAt(1) as? TextView
            action?.text = if (channelModels.isEmpty()) "Выбрать каналы приложений  ›"
            else "Загружаю выбранные каналы…"
            action?.isFocusable = channelModels.isEmpty()
            action?.isClickable = channelModels.isEmpty()
        }
    }

    /** Create one shelf: publisher identity on the left, free-standing artwork on the right. */
    fun updateChannelPrograms(channel: TvChannels.Channel) {
        if (shelves.containsKey(channel.key)) return

        emptyChannelsRow?.let { channelContainer.removeView(it) }
        emptyChannelsRow = null

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            tag = "channel:" + channel.key
        }

        val identity = buildChannelIdentity(channel)
        row.addView(identity.root, LayoutParams(identityWidth, (normal.rowHeight * scale).roundToInt()))

        val scroll = horizontalRail()
        val cards = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(cards, FrameLayout.LayoutParams(-2, -1))
        row.addView(scroll, LayoutParams(0, (normal.rowHeight * scale).roundToInt(), 1f))

        val shelf = Shelf(
            channel = channel,
            row = row,
            identity = identity.root,
            icon = identity.icon,
            appName = identity.appName,
            channelName = identity.channelName,
            cards = cards,
            rowHeight = normal.rowHeight,
            artHeight = normal.artHeight,
            captionHeight = normal.captionHeight,
            iconSize = normal.iconSize
        )

        val desiredIndex = channelModels.indexOfFirst { it.key == channel.key }
        val insertAt = if (desiredIndex <= 0) 0 else
            channelModels.take(desiredIndex).count { shelves.containsKey(it.key) }

        channelContainer.addView(row, insertAt, LayoutParams(-1, (normal.rowHeight * scale).roundToInt()).apply {
            bottomMargin = rowGap
        })
        shelves[channel.key] = shelf

        populateChannelCards(shelf)
        updateDesiredHeight()
        requestLayout()
        if (focusedChannelKey != null) focusChannelShelf(focusedChannelKey)
    }

    private data class ChannelIdentity(
        val root: LinearLayout,
        val icon: ImageView,
        val appName: TextView,
        val channelName: TextView?
    )

    private fun buildChannelIdentity(channel: TvChannels.Channel): ChannelIdentity {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            clipChildren = true
            clipToPadding = true
        }

        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = false
            try {
                setImageDrawable(context.packageManager.getApplicationIcon(channel.packageName))
            } catch (_: Exception) {
                setImageDrawable(null)
            }
        }
        panel.addView(icon, LayoutParams((normal.iconSize * scale).roundToInt(), (normal.iconSize * scale).roundToInt()))

        val appName = TextView(context).apply {
            text = channel.appName
            textSize = normal.appNameSp * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            isSingleLine = false
        }
        panel.addView(appName, LayoutParams(-1, (46 * scale).roundToInt()))

        val channelName = if (channel.name.isNotBlank() && !channel.name.equals(channel.appName, true)) {
            TextView(context).apply {
                text = channel.name
                textSize = normal.channelNameSp * scale
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                isSingleLine = true
            }.also { panel.addView(it, LayoutParams(-1, (28 * scale).roundToInt())) }
        } else null

        return ChannelIdentity(panel, icon, appName, channelName)
    }

    private fun populateChannelCards(shelf: Shelf) {
        val programs = shelf.channel.programs.filter { it.posterUri.isNotBlank() }
        if (programs.isEmpty()) {
            val note = TextView(context).apply {
                text = "В этом канале нет изображений программ"
                textSize = 15f * scale
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER_VERTICAL
            }
            shelf.cards.addView(note, LayoutParams((340 * scale).roundToInt(), -1))
            return
        }

        programs.forEachIndexed { index, program ->
            val root = LinearLayout(context).apply {
                tag = "program:" + shelf.channel.key + ":" + index
                orientation = VERTICAL
                isFocusable = true
                isClickable = true
                visibility = View.GONE // Hidden until a real poster has been decoded successfully.
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 5f * scale
                    setStroke(0, Color.TRANSPARENT)
                }
                setOnFocusChangeListener { view, focused ->
                    val bg = view.background as? GradientDrawable
                    bg?.setStroke(
                        if (focused) (3 * scale).roundToInt().coerceAtLeast(1) else 0,
                        if (focused) 0xFFFFD28A.toInt() else Color.TRANSPARENT
                    )
                    view.scaleX = if (focused) 1.025f else 1f
                    view.scaleY = if (focused) 1.025f else 1f
                    view.elevation = if (focused) 10f * scale else 0f
                    if (focused) {
                        focusChannelShelf(shelf.channel.key)
                        host.onKotlinAppFocused(program.title)
                        host.ensureKotlinFocusVisible(view)
                    }
                }
                setOnClickListener { host.launchTvProgramFromKotlin(program) }
            }

            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
            }
            root.addView(image, LayoutParams(-1, (normal.artHeight * scale).roundToInt()))

            val title = TextView(context).apply {
                text = program.title
                textSize = normal.captionSp * scale
                setTextColor(Color.WHITE)
                maxLines = 2
                gravity = Gravity.TOP or Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                isFocusable = false
                setPadding((6 * scale).roundToInt(), (7 * scale).roundToInt(), (4 * scale).roundToInt(), 0)
            }
            root.addView(title, LayoutParams(-1, (normal.captionHeight * scale).roundToInt()))

            val posterCard = PosterCard(root, image, title)
            shelf.posterCards.add(posterCard)
            shelf.cards.addView(root, LayoutParams(
                cardWidth(normal.artHeight, posterCard.aspectRatio),
                ((normal.artHeight + normal.captionHeight) * scale).roundToInt()
            ).apply {
                rightMargin = (18 * scale).roundToInt()
                topMargin = (4 * scale).roundToInt()
                bottomMargin = (4 * scale).roundToInt()
            })
            loadPoster(program.posterUri, shelf, posterCard)
        }
    }

    private fun cardWidth(artHeightDp: Int, aspectRatio: Float): Int {
        // Keep the source poster shape: a portrait poster is narrow; a widescreen banner is broad.
        val safeRatio = aspectRatio.coerceIn(0.58f, 1.82f)
        val widthDp = (artHeightDp * safeRatio).roundToInt().coerceIn(132, 390)
        return (widthDp * scale).roundToInt()
    }

    private fun focusChannelShelf(key: String?) {
        if (focusedChannelKey == key) return
        focusedChannelKey = key

        val focusIndex = if (key == null) -1 else channelModels.indexOfFirst { it.key == key }
        shelves.values.forEach { shelf ->
            val index = channelModels.indexOfFirst { it.key == shelf.channel.key }
            val target = when {
                key != null && shelf.channel.key == key -> expanded
                focusIndex > 0 && index == focusIndex - 1 -> compact
                else -> normal
            }
            animateShelf(shelf, target)
        }
        updateDesiredHeight()
        requestLayout()
        post { host.refreshKotlinHomeHeight() }
    }

    private fun animateShelf(shelf: Shelf, target: ShelfMetrics) {
        shelf.animator?.cancel()
        val startRow = shelf.rowHeight
        val startArt = shelf.artHeight
        val startCaption = shelf.captionHeight
        val startIcon = shelf.iconSize
        val targetRow = target.rowHeight
        val targetArt = target.artHeight
        val targetCaption = target.captionHeight
        val targetIcon = target.iconSize

        if (startRow == targetRow && startArt == targetArt && startIcon == targetIcon) {
            applyShelfMetrics(shelf, target)
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 230L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val f = animation.animatedValue as Float
                val rowHeight = lerp(startRow, targetRow, f)
                val artHeight = lerp(startArt, targetArt, f)
                val captionHeight = lerp(startCaption, targetCaption, f)
                val iconSize = lerp(startIcon, targetIcon, f)

                shelf.row.layoutParams = (shelf.row.layoutParams as LayoutParams).apply {
                    height = (rowHeight * scale).roundToInt()
                }
                shelf.identity.layoutParams = (shelf.identity.layoutParams as LayoutParams).apply {
                    height = (rowHeight * scale).roundToInt()
                }
                shelf.icon.layoutParams = (shelf.icon.layoutParams as LayoutParams).apply {
                    width = (iconSize * scale).roundToInt()
                    height = (iconSize * scale).roundToInt()
                }

                shelf.posterCards.forEach { card ->
                    applyCardMetrics(shelf, card, artHeight, captionHeight, target.captionSp)
                }
                shelf.appName.setTextSize(target.appNameSp * scale)
                shelf.channelName?.setTextSize(target.channelNameSp * scale)
                shelf.rowHeight = rowHeight
                shelf.artHeight = artHeight
                shelf.captionHeight = captionHeight
                shelf.iconSize = iconSize
            }
            start()
        }
        shelf.animator = animator
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (shelf.animator === animator) {
                    shelf.animator = null
                    applyShelfMetrics(shelf, target)
                    updateDesiredHeight()
                    host.refreshKotlinHomeHeight()
                }
            }
        })
    }

    private fun applyShelfMetrics(shelf: Shelf, metrics: ShelfMetrics) {
        shelf.rowHeight = metrics.rowHeight
        shelf.artHeight = metrics.artHeight
        shelf.captionHeight = metrics.captionHeight
        shelf.iconSize = metrics.iconSize

        shelf.row.layoutParams = (shelf.row.layoutParams as LayoutParams).apply {
            height = (metrics.rowHeight * scale).roundToInt()
        }
        shelf.identity.layoutParams = (shelf.identity.layoutParams as LayoutParams).apply {
            height = (metrics.rowHeight * scale).roundToInt()
        }
        shelf.icon.layoutParams = (shelf.icon.layoutParams as LayoutParams).apply {
            width = (metrics.iconSize * scale).roundToInt()
            height = (metrics.iconSize * scale).roundToInt()
        }
        shelf.appName.setTextSize(metrics.appNameSp * scale)
        shelf.channelName?.setTextSize(metrics.channelNameSp * scale)
        shelf.posterCards.forEach { card ->
            applyCardMetrics(shelf, card, metrics.artHeight, metrics.captionHeight, metrics.captionSp)
        }
    }

    private fun applyCardMetrics(
        shelf: Shelf,
        card: PosterCard,
        artHeightDp: Int,
        captionHeightDp: Int,
        captionSp: Float
    ) {
        val cardWidthPx = cardWidth(artHeightDp, card.aspectRatio)
        card.root.layoutParams = (card.root.layoutParams as LayoutParams).apply {
            width = cardWidthPx
            height = ((artHeightDp + captionHeightDp) * scale).roundToInt()
        }
        card.image.layoutParams = (card.image.layoutParams as LayoutParams).apply {
            height = (artHeightDp * scale).roundToInt()
        }
        card.title.layoutParams = (card.title.layoutParams as LayoutParams).apply {
            height = (captionHeightDp * scale).roundToInt()
        }
        card.title.setTextSize(captionSp * scale)
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int =
        (start + (end - start) * fraction).roundToInt()

    private fun updateDesiredHeight() {
        // Reserve enough room for one expanded shelf so the last row can grow without clipping.
        val rows = shelves.size + if (emptyChannelsRow != null) 1 else 0
        desiredHeight = ((208 + rows * (normal.rowHeight + 30) + 110) * scale).roundToInt()
    }

    private fun horizontalRail(): HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = true
        clipToPadding = true
        setPadding(0, (8 * scale).roundToInt(), (8 * scale).roundToInt(), (8 * scale).roundToInt())
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun loadPoster(raw: String, shelf: Shelf, card: PosterCard) {
        LauncherPosterCache.get(raw)?.let { cached ->
            applyPoster(shelf, card, cached, raw)
            return
        }

        LauncherPosterQueue.executor.execute {
            try {
                val uri = Uri.parse(raw)
                val bitmap = if (uri.scheme == "http" || uri.scheme == "https") {
                    val connection = URL(raw).openConnection()
                    connection.connectTimeout = 6000
                    connection.readTimeout = 9000
                    connection.useCaches = true
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    if (connection is HttpURLConnection) connection.instanceFollowRedirects = true
                    connection.getInputStream().use { source ->
                        BufferedInputStream(source).use { input ->
                            input.mark(64 * 1024)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(input, null, bounds)
                            input.reset()
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            BitmapFactory.decodeStream(input, null, options)
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { source ->
                        BufferedInputStream(source).use { input ->
                            input.mark(64 * 1024)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(input, null, bounds)
                            input.reset()
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                            BitmapFactory.decodeStream(input, null, options)
                        }
                    }
                }

                if (bitmap != null) {
                    LauncherPosterCache.put(raw, bitmap)
                    host.runOnUiThread { applyPoster(shelf, card, bitmap, raw) }
                } else {
                    host.runOnUiThread { card.root.visibility = View.GONE }
                }
            } catch (_: Exception) {
                host.runOnUiThread { card.root.visibility = View.GONE }
            }
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 2
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > 1100) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun applyPoster(shelf: Shelf, card: PosterCard, bitmap: Bitmap, raw: String) {
        if (!card.root.isAttachedToWindow) return
        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        card.aspectRatio = ratio.coerceIn(0.58f, 1.82f)
        card.image.scaleType = ImageView.ScaleType.FIT_CENTER
        card.image.setImageBitmap(bitmap)
        applyCardMetrics(shelf, card, shelf.artHeight, shelf.captionHeight, normal.captionSp)
        card.root.visibility = View.VISIBLE
        shelf.cards.requestLayout()
        shelf.row.requestLayout()
    }
}
