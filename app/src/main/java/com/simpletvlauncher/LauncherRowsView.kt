package com.simpletvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Android TV-style home:
 * - the applications rail has a fixed identity panel on the left and app banners on the right;
 * - every selected preview channel is a separate row, with its publisher on the left
 *   and that channel's programmes on the right.
 */
private object LauncherPosterQueue {
    // Keep poster decoding away from the shared catalogue/weather executor.
    // No poster request is silently discarded: the queue drains steadily in the background.
    val executor = ThreadPoolExecutor(
        3, 3, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(),
        { task -> Thread(task, "launcher-poster").apply { isDaemon = true } }
    )
}

class LauncherRowsView(
    context: Context,
    private val host: MainActivity,
    private val prefs: Prefs
) : LinearLayout(context) {
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
    private val channelCardContainers = LinkedHashMap<String, LinearLayout>()
    private val channelShelfContainers = LinkedHashMap<String, LinearLayout>()
    private var channelModels: List<TvChannels.Channel> = emptyList()
    private var emptyChannelsRow: View? = null

    private val sideWidth = (200 * scale).toInt()
    private val channelRowHeight = (280 * scale).toInt()

    init {
        orientation = VERTICAL
        appRail.orientation = HORIZONTAL
        channelContainer.orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding((58 * scale).toInt(), (12 * scale).toInt(),
            (58 * scale).toInt(), (38 * scale).toInt())

        appRow.orientation = HORIZONTAL
        appRow.gravity = Gravity.CENTER_VERTICAL
        appRow.clipChildren = false
        appRow.clipToPadding = false

        val appsSide = buildAppsSidePanel()
        appRow.addView(appsSide, LayoutParams(sideWidth, (190 * scale).toInt()))

        val appScroll = horizontalRail()
        appScroll.addView(appRail, FrameLayout.LayoutParams(-2, -1))
        appRow.addView(appScroll, LayoutParams(0, (190 * scale).toInt(), 1f))
        addView(appRow, LayoutParams(-1, (202 * scale).toInt()))

        channelContainer.orientation = VERTICAL
        channelContainer.clipChildren = false
        channelContainer.clipToPadding = false
        addView(channelContainer, LayoutParams(-1, -2))
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
        allAppsButton?.let { button ->
            button.post { if (button.isAttachedToWindow) button.requestFocus() }
        }
    }

    fun focusAppAt(index: Int) {
        val target = appRail.getChildAt(index)
        if (target != null && target.isFocusable) target.requestFocus() else focusFirst()
    }

    fun needsChannelPrograms(channel: TvChannels.Channel): Boolean =
        !channelCardContainers.containsKey(channel.key)

    private fun buildAppsSidePanel(): View {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            tag = "all_apps"
            setOnClickListener { host.openAllAppsPage() }
        }
        val icon = TextView(context).apply {
            text = "▦"
            textSize = 48f * scale
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE53935.toInt())
            }
            isFocusable = false
        }
        panel.addView(icon, LayoutParams((88 * scale).toInt(), (88 * scale).toInt()))
        val label = TextView(context).apply {
            text = "Приложения"
            textSize = 20f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = false
        }
        panel.addView(label, LayoutParams(-1, (48 * scale).toInt()))
        panel.setOnFocusChangeListener { view, focused ->
            view.alpha = if (focused) 1f else .88f
            if (focused) host.ensureKotlinFocusVisible(view)
        }
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
                        host.onKotlinAppFocused(item.label)
                        host.ensureKotlinFocusVisible(view)
                    }
                }
                setOnLongClickListener {
                    host.openKotlinAppMenu(this)
                    true
                }
            }
            appRail.addView(tile, LayoutParams((276 * scale).toInt(), (164 * scale).toInt()).apply {
                rightMargin = (16 * scale).toInt()
                topMargin = (8 * scale).toInt()
                bottomMargin = (8 * scale).toInt()
            })
        }
    }

    private fun buildChannelRails(channels: List<TvChannels.Channel>) {
        val wanted = channels.map { it.key }.toSet()
        val focusedTag = findFocus()?.tag as? String

        val removed = channelShelfContainers.keys.filter { it !in wanted }
        removed.forEach { key ->
            channelShelfContainers.remove(key)?.let { channelContainer.removeView(it) }
            channelCardContainers.remove(key)
        }
        channelModels = channels

        // Reorder existing channel rows without rebuilding their programme cards.
        channels.forEach { channel ->
            channelShelfContainers[channel.key]?.let { row ->
                channelContainer.removeView(row)
                channelContainer.addView(row)
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
        if (channelShelfContainers.isEmpty()) {
            if (emptyChannelsRow == null) {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val left = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    addView(TextView(context).apply {
                        text = "▤"
                        textSize = 42f * scale
                        setTextColor(0xFFE7E7E7.toInt())
                        gravity = Gravity.CENTER
                    }, LayoutParams((100 * scale).toInt(), (88 * scale).toInt()))
                    addView(TextView(context).apply {
                        text = "Каналы"
                        textSize = 21f * scale
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                    }, LayoutParams(-1, (44 * scale).toInt()))
                }
                row.addView(left, LayoutParams(sideWidth, channelRowHeight))
                val action = TextView(context).apply {
                    text = if (channelModels.isEmpty())
                        "Выбрать каналы приложений  ›"
                    else
                        "Загружаю выбранные каналы…"
                    textSize = 20f * scale
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((20 * scale).toInt(), 0, (20 * scale).toInt(), 0)
                    isFocusable = channelModels.isEmpty()
                    isClickable = channelModels.isEmpty()
                    background = GradientDrawable().apply {
                        setColor(0x332E7770)
                        cornerRadius = 14f * scale
                    }
                    if (channelModels.isEmpty()) setOnClickListener {
                        host.openChannelSettingsFromKotlin()
                    }
                }
                row.addView(action, LayoutParams(0, (64 * scale).toInt(), 1f))
                channelContainer.addView(row, LayoutParams(-1, channelRowHeight).apply {
                    bottomMargin = (26 * scale).toInt()
                })
                emptyChannelsRow = row
            } else {
                val action = (emptyChannelsRow as? ViewGroup)?.getChildAt(1) as? TextView
                action?.text = if (channelModels.isEmpty())
                    "Выбрать каналы приложений  ›"
                else
                    "Загружаю выбранные каналы…"
                action?.isFocusable = channelModels.isEmpty()
                action?.isClickable = channelModels.isEmpty()
                action?.setOnClickListener(if (channelModels.isEmpty()) {
                    View.OnClickListener { host.openChannelSettingsFromKotlin() }
                } else null)
            }
        } else {
            emptyChannelsRow?.let { channelContainer.removeView(it) }
            emptyChannelsRow = null
        }
    }

    /** Add one selected channel in the Android TV layout: app identity left, programmes right. */
    fun updateChannelPrograms(channel: TvChannels.Channel) {
        if (channelShelfContainers.containsKey(channel.key)) return
        emptyChannelsRow?.let { channelContainer.removeView(it) }
        emptyChannelsRow = null

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Keep the channel identity column separate from the scrolling posters.
            clipChildren = true
            clipToPadding = true
            tag = "channel:" + channel.key
        }

        val identity = buildChannelIdentity(channel)
        row.addView(identity, LayoutParams(sideWidth, channelRowHeight))

        val scroll = horizontalRail()
        val cards = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(cards, FrameLayout.LayoutParams(-2, -1))
        row.addView(scroll, LayoutParams(0, channelRowHeight, 1f))

        val desiredIndex = channelModels.indexOfFirst { it.key == channel.key }
        var insertAt = 0
        if (desiredIndex > 0) {
            for (i in 0 until desiredIndex) {
                if (channelShelfContainers.containsKey(channelModels[i].key)) insertAt++
            }
        }
        channelContainer.addView(row, insertAt, LayoutParams(-1, channelRowHeight).apply {
            bottomMargin = (26 * scale).toInt()
        })
        channelShelfContainers[channel.key] = row
        channelCardContainers[channel.key] = cards

        if (channel.programs.isEmpty()) {
            addChannelPlaceholder(cards, "В этом канале пока нет программ")
        } else {
            populateChannelCards(channel, cards)
        }
        updateDesiredHeight()
        requestLayout()
    }

    private fun buildChannelIdentity(channel: TvChannels.Channel): View {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF242424.toInt())
            }
            clipToOutline = true
            try {
                val drawable = context.packageManager.getApplicationIcon(channel.packageName)
                setImageDrawable(drawable)
            } catch (_: Exception) {
                setImageDrawable(null)
            }
        }
        panel.addView(icon, LayoutParams((68 * scale).toInt(), (68 * scale).toInt()))
        panel.addView(TextView(context).apply {
            text = channel.appName
            textSize = 21f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }, LayoutParams((sideWidth - 12 * scale).toInt(), (42 * scale).toInt()))
        if (channel.name.isNotBlank() && !channel.name.equals(channel.appName, true)) {
            panel.addView(TextView(context).apply {
                text = channel.name
                textSize = 14f * scale
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
            }, LayoutParams((sideWidth - 12 * scale).toInt(), (28 * scale).toInt()))
        }
        return panel
    }

    private fun addChannelPlaceholder(cards: LinearLayout, message: String) {
        val placeholder = TextView(context).apply {
            text = message
            textSize = 16f * scale
            setTextColor(0xCCFFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * scale).toInt(), 0, (18 * scale).toInt(), 0)
            isFocusable = false
        }
        cards.addView(placeholder, LayoutParams((300 * scale).toInt(), (80 * scale).toInt()))
    }

    private fun populateChannelCards(channel: TvChannels.Channel, cards: LinearLayout) {
        channel.programs.take(16).forEachIndexed { index, program ->
            val card = LinearLayout(context).apply {
                tag = "program:" + channel.key + ":" + index
                orientation = VERTICAL
                isFocusable = true
                isClickable = true
                background = GradientDrawable().apply {
                    // Poster-first card: transparent outside the artwork, focus outline only.
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 8f * scale
                    setStroke(0, Color.TRANSPARENT)
                }
                setPadding((4 * scale).toInt(), (4 * scale).toInt(), (4 * scale).toInt(), (4 * scale).toInt())
                setOnFocusChangeListener { view, focused ->
                    val bg = view.background as? GradientDrawable
                    bg?.setStroke(
                        (if (focused) 3 * scale else 0f).toInt().coerceAtLeast(0),
                        if (focused) 0xFFFFD28A.toInt() else Color.TRANSPARENT
                    )
                    view.alpha = 1f
                    if (focused) {
                        host.onKotlinAppFocused(program.title)
                        host.ensureKotlinFocusVisible(view)
                    }
                }
                setOnClickListener { host.launchTvProgramFromKotlin(program) }
            }

            val poster = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = false
                // Keep a real visual fallback while the provider artwork is loading,
                // or when that programme has no poster URL.
                try { setImageDrawable(context.packageManager.getApplicationIcon(channel.packageName)) }
                catch (_: Exception) { }
            }
            card.addView(poster, LayoutParams(-1, (196 * scale).toInt()))
            card.addView(TextView(context).apply {
                text = program.title
                textSize = 14f * scale
                setTextColor(Color.WHITE)
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
            }, LayoutParams(-1, (42 * scale).toInt()))

            cards.addView(card, LayoutParams((236 * scale).toInt(), (246 * scale).toInt()).apply {
                rightMargin = (12 * scale).toInt()
                topMargin = (6 * scale).toInt()
                bottomMargin = (6 * scale).toInt()
            })
            loadPoster(program.posterUri, poster, card)
        }
    }

    private fun updateDesiredHeight() {
        desiredHeight = (
            202 + channelShelfContainers.size * 316 +
                (if (emptyChannelsRow != null) 316 else 0) + 50
            ).let { (it * scale).toInt() }
    }

    private fun horizontalRail(): HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = true
        clipToPadding = true
        setPadding(0, (4 * scale).toInt(), (8 * scale).toInt(), (4 * scale).toInt())
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun loadPoster(raw: String?, target: ImageView, card: View) {
        if (raw.isNullOrBlank()) return
        try {
            LauncherPosterQueue.executor.execute {
                try {
                    val uri = Uri.parse(raw)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = if (uri.scheme == "http" || uri.scheme == "https") {
                        URL(raw).openConnection().apply {
                            connectTimeout = 5500
                            readTimeout = 8000
                            useCaches = true
                        }.getInputStream().use { BitmapFactory.decodeStream(it, null, options) }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                    }
                    if (bitmap != null) host.runOnUiThread {
                        if (target.isAttachedToWindow) {
                            val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                            target.scaleType = if (ratio > 1.25f)
                                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                            target.setImageBitmap(bitmap)

                        } else bitmap.recycle()
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) {
            // Queue full: skip this poster rather than blocking the UI thread.
        }
    }
}
