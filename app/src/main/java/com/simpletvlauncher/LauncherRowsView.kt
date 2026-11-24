package com.simpletvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.BitmapFactory
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

/**
 * Kotlin home content: a pinned All apps banner, one horizontal app-banner rail,
 * followed immediately by independently scrollable Android TV preview-channel rails.
 * The existing Java activity continues to own the status/header controls and remote-key policy.
 */
class LauncherRowsView(
    context: Context,
    private val host: MainActivity,
    private val prefs: Prefs
) : LinearLayout(context) {
    private val scale = UI.scale(context)
    private val appRail = LinearLayout(context)
    private val channelContainer = LinearLayout(context)
    private var desiredHeight = 0
    private var appsSignature = ""
    private var channelsSignature = ""
    private var appsBuilt = false
    private var channelsBuilt = false
    private var allAppsButton: TextView? = null
    private var allChannels: List<TvChannels.Channel> = emptyList()
    private var renderedChannels = 0
    private var moreButton: TextView? = null
    private var lastAddedProgram: View? = null
    private val channelBatchSize = 6

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding((68 * scale).toInt(), (18 * scale).toInt(), (68 * scale).toInt(), (42 * scale).toInt())
        addView(sectionTitle("Приложения"), LayoutParams(-1, (42 * scale).toInt()))
        val appScroll = horizontalRail()
        appScroll.addView(appRail, FrameLayout.LayoutParams(-2, -2))
        addView(appScroll, LayoutParams(-1, (188 * scale).toInt()))
        addView(channelContainer, LayoutParams(-1, -2))
    }

    fun contentHeight(): Int = desiredHeight

    fun render(apps: List<AppRepo.AppItem>, channels: List<TvChannels.Channel>) {
        // Rebuild only the rail whose data changed. TV refreshes must not detach the
        // currently focused app tile while the user is navigating with the remote.
        val focusedTag = findFocus()?.tag as? String
        val nextAppsSignature = apps.joinToString("|") { it.pkg + ":" + it.cls + ":" + it.label }
        // Program lists are populated in later batches, so the signature is based only on channel identity/order.
        val nextChannelsSignature = channels.joinToString("|") { channel -> channel.key }

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
        // A TV row consists of a 12dp title gap, a 42dp title and a 178dp card rail.
        updateDesiredHeight()
        requestLayout()

        post {
            val target = if (!focusedTag.isNullOrBlank()) findViewWithTag<View>(focusedTag) else null
            if (target != null && target.isFocusable && !target.hasFocus()) {
                target.requestFocus()
            }
        }
    }

    fun focusFirst() {
        allAppsButton?.let { button ->
            button.post { if (button.isAttachedToWindow) button.requestFocus() }
        }
    }

    fun focusAppAt(index: Int) {
        val target = appRail.getChildAt(index + 1)
        if (target != null && target.isFocusable) target.requestFocus() else focusFirst()
    }

    fun hasFocusedItem(): Boolean = findFocus() != null

    private fun buildAppRail(apps: List<AppRepo.AppItem>) {
        appRail.removeAllViews()
        val all = TextView(context).apply {
            tag = "all_apps"
            text = "Все программы"
            textSize = 20f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x553F6F68)
                cornerRadius = 18f * scale
                setStroke((2 * scale).toInt().coerceAtLeast(1), 0x88FFFFFF.toInt())
            }
            setOnFocusChangeListener { v, focused ->
                if (focused) host.ensureKotlinFocusVisible(v)
                alpha = if (focused) 1f else .86f
            }
            setOnClickListener { host.openAllAppsPage() }
        }
        allAppsButton = all
        val pinned = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(context).apply {
                text = "▦"
                textSize = 42f * scale
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                isFocusable = false
            })
            addView(all, LayoutParams((220 * scale).toInt(), (54 * scale).toInt()))
        }
        appRail.addView(pinned, LayoutParams((240 * scale).toInt(), (172 * scale).toInt()).apply {
            rightMargin = (18 * scale).toInt()
        })

        apps.forEach { item ->
            val tile = MainActivity.AppTile(context, item, prefs.themeColor()).apply {
                tag = "app:" + item.pkg
                radius = 18f * scale
                isFocusable = true
                setOnClickListener { host.launchFromKotlin(item) }
                setOnFocusChangeListener { v, focused ->
                    if (focused) {
                        host.onKotlinAppFocused(item.label)
                        host.ensureKotlinFocusVisible(v)
                    }
                }
                setOnLongClickListener {
                    host.openKotlinAppMenu(this)
                    true
                }
            }
            appRail.addView(tile, LayoutParams((276 * scale).toInt(), (164 * scale).toInt()).apply {
                rightMargin = (16 * scale).toInt()
                topMargin = (4 * scale).toInt()
            })
        }
    }

    private fun buildChannelRails(channels: List<TvChannels.Channel>) {
        channelContainer.removeAllViews()
        allChannels = channels
        renderedChannels = 0
        moreButton = null
        lastAddedProgram = null
        appendMoreChannels()
    }

    fun renderedChannelCount(): Int = renderedChannels

    fun setMoreLoading(loading: Boolean) {
        moreButton?.isEnabled = !loading
        moreButton?.text = if (loading) "Загружаю каналы…" else moreButtonLabel()
        moreButton?.alpha = if (loading) .65f else 1f
    }

    /** Appends at most six channel shelves, avoiding hundreds of simultaneous poster cards. */
    fun appendMoreChannels() {
        val hadMoreFocus = moreButton?.hasFocus() == true
        moreButton?.let { channelContainer.removeView(it) }
        moreButton = null
        lastAddedProgram = null

        val end = (renderedChannels + channelBatchSize).coerceAtMost(allChannels.size)
        for (index in renderedChannels until end) {
            lastAddedProgram = addChannelRow(allChannels[index], index) ?: lastAddedProgram
        }
        renderedChannels = end
        if (renderedChannels < allChannels.size) addMoreButton()
        updateDesiredHeight()
        requestLayout()

        if (hadMoreFocus) {
            val target = moreButton ?: lastAddedProgram
            target?.post { if (target.isFocusable) target.requestFocus() }
        }
    }

    private fun addChannelRow(channel: TvChannels.Channel, channelIndex: Int): View? {
        val title = sectionTitle(channel.name)
        title.contentDescription = channel.name + " — " + channel.appName
        channelContainer.addView(title, LayoutParams(-1, (42 * scale).toInt()).apply {
            topMargin = (12 * scale).toInt()
        })
        val scroll = horizontalRail()
        val cards = LinearLayout(context).apply {
            orientation = HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        var lastFocusable: View? = null
        channel.programs.take(12).forEachIndexed { index, program ->
            val card = LinearLayout(context).apply {
                tag = "program:" + channel.key + ":" + index
                orientation = VERTICAL
                isFocusable = true
                isClickable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x3AFFFFFF)
                    cornerRadius = 14f * scale
                    setStroke((1 * scale).toInt().coerceAtLeast(1), 0x44FFFFFF)
                }
                setPadding((6 * scale).toInt(), (6 * scale).toInt(), (6 * scale).toInt(), (6 * scale).toInt())
                setOnFocusChangeListener { v, focused ->
                    val bg = v.background as? android.graphics.drawable.GradientDrawable
                    bg?.setStroke(
                        (if (focused) 3 * scale else 1 * scale).toInt().coerceAtLeast(1),
                        if (focused) 0xFFFFD28A.toInt() else 0x44FFFFFF
                    )
                    v.alpha = if (focused) 1f else .86f
                    if (focused) {
                        host.onKotlinAppFocused(program.title)
                        host.ensureKotlinFocusVisible(v)
                    }
                }
                setOnClickListener { host.launchTvProgramFromKotlin(program) }
            }
            val poster = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x33202020)
                isFocusable = false
            }
            card.addView(poster, LayoutParams(-1, (112 * scale).toInt()))
            val label = TextView(context).apply {
                text = program.title
                textSize = 15f * scale
                setTextColor(Color.WHITE)
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = false
            }
            card.addView(label, LayoutParams(-1, (44 * scale).toInt()))
            cards.addView(card, LayoutParams((196 * scale).toInt(), (166 * scale).toInt()).apply {
                rightMargin = (12 * scale).toInt()
            })
            loadPoster(program.posterUri, poster)
            lastFocusable = card
        }
        if (channel.programs.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Программы канала не загружены"
                textSize = 16f * scale
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.CENTER
                isFocusable = false
            }
            cards.addView(empty, LayoutParams((320 * scale).toInt(), (166 * scale).toInt()))
        }
        scroll.addView(cards, FrameLayout.LayoutParams(-2, -2))
        channelContainer.addView(scroll, LayoutParams(-1, (178 * scale).toInt()))
        return lastFocusable
    }

    private fun addMoreButton() {
        val button = TextView(context).apply {
            tag = "more_channels"
            text = moreButtonLabel()
            textSize = 19f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x553F6F68)
                cornerRadius = 14f * scale
                setStroke((1 * scale).toInt().coerceAtLeast(1), 0x88FFFFFF.toInt())
            }
            setOnFocusChangeListener { v, focused ->
                val bg = v.background as? android.graphics.drawable.GradientDrawable
                bg?.setStroke(
                    (if (focused) 3 * scale else 1 * scale).toInt().coerceAtLeast(1),
                    if (focused) 0xFFFFD28A.toInt() else 0x88FFFFFF.toInt()
                )
                if (focused) host.ensureKotlinFocusVisible(v)
            }
            setOnClickListener { host.loadMoreKotlinChannels() }
        }
        moreButton = button
        channelContainer.addView(button, LayoutParams((380 * scale).toInt(), (58 * scale).toInt()).apply {
            topMargin = (12 * scale).toInt()
        })
    }

    private fun moreButtonLabel(): String {
        val left = (allChannels.size - renderedChannels).coerceAtLeast(0)
        return "Показать ещё каналы  ·  осталось $left"
    }

    private fun updateDesiredHeight() {
        val moreHeight = if (renderedChannels < allChannels.size) 70 else 0
        desiredHeight = (42 + 188 + 18 + renderedChannels * 232 + moreHeight + 42).let { (it * scale).toInt() }
    }

    private fun sectionTitle(value: String): TextView = TextView(context).apply {
        text = value
        textSize = 25f * scale
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun horizontalRail(): HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        setPadding(0, (4 * scale).toInt(), (8 * scale).toInt(), (8 * scale).toInt())
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun loadPoster(raw: String?, target: ImageView) {
        if (raw.isNullOrBlank()) return
        Weather.IO.execute {
            try {
                val uri = Uri.parse(raw)
                val bitmap = if (uri.scheme == "http" || uri.scheme == "https") {
                    URL(raw).openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 7000
                    }.getInputStream().use { BitmapFactory.decodeStream(it) }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
                if (bitmap != null) host.runOnUiThread { target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }
}
