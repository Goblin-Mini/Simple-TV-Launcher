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
import java.util.concurrent.Executors

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
    private val imagePool = Executors.newFixedThreadPool(3)
    private val appRail = LinearLayout(context)
    private val channelContainer = LinearLayout(context)
    private var desiredHeight = 0

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
        appRail.removeAllViews()
        channelContainer.removeAllViews()

        val all = TextView(context).apply {
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
            setOnClickListener { host.openAllAppsPage() }
        }
        val pinned = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(context).apply { text = "▦"; textSize = 42f * scale; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
            addView(all, LayoutParams((220 * scale).toInt(), (54 * scale).toInt()))
        }
        appRail.addView(pinned, LayoutParams((240 * scale).toInt(), (172 * scale).toInt()).apply {
            rightMargin = (18 * scale).toInt()
        })

        apps.forEach { item ->
            val tile = MainActivity.AppTile(context, item, prefs.themeColor()).apply {
                radius = 18f * scale
                isFocusable = true
                setOnClickListener { host.launchFromKotlin(item) }
                setOnFocusChangeListener { v, focused ->
                    if (focused) host.onKotlinAppFocused(item.label)
                    invalidate()
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

        channels.forEach { channel ->
            val title = sectionTitle(channel.name)
            title.text = channel.name
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
            channel.programs.take(14).forEach { program ->
                val card = LinearLayout(context).apply {
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
                        v.alpha = if (focused) 1f else .82f
                        if (focused) host.onKotlinAppFocused(program.title)
                    }
                    setOnClickListener { host.launchTvProgramFromKotlin(program) }
                }
                val poster = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0x33202020)
                }
                card.addView(poster, LayoutParams(-1, (112 * scale).toInt()))
                val label = TextView(context).apply {
                    text = program.title
                    textSize = 15f * scale
                    setTextColor(Color.WHITE)
                    maxLines = 2
                    gravity = Gravity.CENTER_VERTICAL
                }
                card.addView(label, LayoutParams(-1, (44 * scale).toInt()))
                cards.addView(card, LayoutParams((196 * scale).toInt(), (166 * scale).toInt()).apply {
                    rightMargin = (12 * scale).toInt()
                })
                loadPoster(program.posterUri, poster)
            }
            scroll.addView(cards, FrameLayout.LayoutParams(-2, -2))
            channelContainer.addView(scroll, LayoutParams(-1, (178 * scale).toInt()))
        }
        desiredHeight = (42 + 188 + 18 + channels.size * 220 + 42).let { (it * scale).toInt() }
        requestLayout()
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
        imagePool.execute {
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
