package com.simpletvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Kotlin screen with the complete installed-app catalogue, displayed as TV banners. */
class AllAppsActivity : Activity() {
    private lateinit var rows: LinearLayout
    private lateinit var prefs: Prefs
    private var scale = 1f

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = Prefs(this)
        scale = UI.scale(this)
        val root = FrameLayout(this)
        root.addView(BgView(this, prefs.settingsAccent()), FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((64 * scale).toInt(), (34 * scale).toInt(), (64 * scale).toInt(), (28 * scale).toInt())
        }
        page.addView(TextView(this).apply {
            text = "Все программы"
            textSize = 36f * scale
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }, LinearLayout.LayoutParams(-1, (62 * scale).toInt()))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        scroll.addView(rows)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val back = TextView(this).apply {
            text = "Назад"
            textSize = 20f * scale
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { finish() }
        }
        page.addView(back, LinearLayout.LayoutParams((180 * scale).toInt(), (54 * scale).toInt()))
        root.addView(page)
        setContentView(root)
        Weather.IO.execute {
            val loaded = AppRepo.loadAll(this)
            val visible = AppRepo.visible(loaded, prefs)
            runOnUiThread { render(visible) }
        }
    }

    private fun render(apps: List<AppRepo.AppItem>) {
        rows.removeAllViews()
        val columns = 4
        apps.chunked(columns).forEach { batch ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            batch.forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    isFocusable = true
                    isClickable = true
                    setPadding((8 * scale).toInt(), (8 * scale).toInt(), (8 * scale).toInt(), (8 * scale).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x3AFFFFFF)
                        cornerRadius = 16f * scale
                    }
                    setOnFocusChangeListener { v, focused -> v.alpha = if (focused) 1f else .82f }
                    setOnClickListener {
                        val intent = Intent(Intent.ACTION_MAIN).setComponent(item.component())
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        try { startActivity(intent) } catch (_: Exception) {
                            packageManager.getLeanbackLaunchIntentForPackage(item.pkg)?.let { startActivity(it) }
                        }
                    }
                }
                val banner = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val drawable = item.banner ?: item.icon
                    setImageDrawable(drawable)
                }
                card.addView(banner, LinearLayout.LayoutParams(-1, (116 * scale).toInt()))
                card.addView(TextView(this).apply {
                    text = item.label
                    textSize = 16f * scale
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, LinearLayout.LayoutParams(-1, (42 * scale).toInt()))
                line.addView(card, LinearLayout.LayoutParams(0, (176 * scale).toInt(), 1f).apply {
                    rightMargin = (14 * scale).toInt()
                    bottomMargin = (14 * scale).toInt()
                })
            }
            rows.addView(line)
        }
        if (apps.isEmpty()) rows.addView(TextView(this).apply {
            text = "Нет установленных приложений"
            setTextColor(Color.WHITE)
            textSize = 20f * scale
        })
        rows.getChildAt(0)?.let { firstRow ->
            (firstRow as? ViewGroup)?.getChildAt(0)?.let { firstCard ->
                firstCard.post { firstCard.requestFocus() }
            }
        }
    }

    override fun onBackPressed() { finish() }
}
