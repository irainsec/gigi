package com.aman.gigi.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import com.aman.gigi.ui.chat.ChatBubbleActivity
import kotlin.math.abs

/**
 * Floating chat head — a draggable bubble showing the sender's emoji/Twigi that appears
 * when a message arrives while you're elsewhere. Tap to open the chat, fling it near the
 * bottom (or long-press) to dismiss. Replaces the old behaviour of yanking the whole chat
 * Activity to the foreground on every incoming message.
 */
class ChatHeadService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var badge: TextView? = null
    private var avatar: ImageView? = null
    private var initial: TextView? = null

    private var connectionId: String = ""
    private var senderName: String = ""
    private var unread: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val cid = intent?.getStringExtra(EXTRA_CONNECTION_ID).orEmpty()
        if (cid.isBlank()) { stopSelf(); return START_NOT_STICKY }

        val emojiUrl = intent?.getStringExtra(EXTRA_EMOJI_URL).orEmpty()
        senderName = intent?.getStringExtra(EXTRA_SENDER_NAME).orEmpty().ifBlank { "Chat" }

        if (bubble != null && cid == connectionId) {
            unread++                                    // same chat → just bump the badge
            badge?.text = if (unread > 9) "9+" else unread.toString()
            badge?.visibility = View.VISIBLE
            return START_NOT_STICKY
        }
        if (bubble != null) removeBubble()              // different chat → swap the bubble

        connectionId = cid
        unread = 1
        showBubble(emojiUrl)
        return START_NOT_STICKY
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun showBubble(emojiUrl: String) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val size = dp(58)
        val root = FrameLayout(this)

        // white disc so any emoji / Twigi reads clearly over any wallpaper
        val disc = ImageView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#8B5CF6"))
            }
            elevation = dp(6).toFloat()
        }
        root.addView(disc, FrameLayout.LayoutParams(size, size))

        avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = dp(7).toFloat()      // must sit ABOVE the white disc (elev 6)
        }
        root.addView(
            avatar,
            FrameLayout.LayoutParams(size - dp(14), size - dp(14)).apply { gravity = Gravity.CENTER }
        )

        initial = TextView(this).apply {
            text = senderName.trim().take(1).uppercase().ifBlank { "💬" }
            setTextColor(Color.parseColor("#8B5CF6"))
            textSize = 20f
            gravity = Gravity.CENTER
            visibility = View.GONE
            elevation = dp(7).toFloat()      // above the disc, same as the avatar
        }
        root.addView(
            initial,
            FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
        )

        badge = TextView(this).apply {
            text = "1"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EC4899"))
            }
            elevation = dp(8).toFloat()
        }
        root.addView(
            badge,
            FrameLayout.LayoutParams(dp(18), dp(18)).apply { gravity = Gravity.TOP or Gravity.END }
        )

        // Fallback: the sender's initial on the disc — never the drab monochrome system
        // icon. Replaced by the real animated emoji / Twigi as soon as it decodes.
        fun showInitial() {
            avatar?.setImageDrawable(null)
            initial?.visibility = View.VISIBLE
        }

        if (emojiUrl.isNotBlank()) {
            val loader = ImageLoader.Builder(this).components {
                if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
                else add(coil.decode.GifDecoder.Factory())
            }.build()
            loader.enqueue(
                ImageRequest.Builder(this).data(emojiUrl)
                    .target(
                        onSuccess = { d ->
                            initial?.visibility = View.GONE
                            avatar?.setImageDrawable(d)
                            // animated WebP/GIF must be explicitly started
                            (d as? android.graphics.drawable.Animatable)?.start()
                        },
                        onError = {
                            android.util.Log.w("ChatHead", "emoji load failed: $emojiUrl")
                            showInitial()
                        }
                    )
                    .build()
            )
        } else {
            android.util.Log.w("ChatHead", "no emoji url for $connectionId — using initial")
            showInitial()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(76)
            y = resources.displayMetrics.heightPixels / 3
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            private var ix = 0; private var iy = 0
            private var tx = 0f; private var ty = 0f
            private var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        lp.x = ix + dx; lp.y = iy + dy
                        runCatching { windowManager?.updateViewLayout(v, lp) }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            openChat()
                        } else if (lp.y > resources.displayMetrics.heightPixels - dp(140)) {
                            stopSelf()                       // flung to the bottom → dismiss
                        } else {
                            val mid = resources.displayMetrics.widthPixels / 2
                            lp.x = if (lp.x + size / 2 < mid) dp(8)
                                   else resources.displayMetrics.widthPixels - size - dp(8)
                            runCatching { windowManager?.updateViewLayout(v, lp) }
                        }
                    }
                }
                return true
            }
        })
        root.setOnLongClickListener { stopSelf(); true }

        bubble = root
        runCatching { wm.addView(root, lp) }.onFailure { stopSelf() }
    }

    private fun openChat() {
        runCatching {
            startActivity(
                Intent(this, ChatBubbleActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("connection_id", connectionId)
                    putExtra("sender_name", senderName)
                }
            )
        }
        stopSelf()
    }

    private fun removeBubble() {
        runCatching { bubble?.let { windowManager?.removeView(it) } }
        bubble = null; badge = null; avatar = null; initial = null
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CONNECTION_ID = "connection_id"
        private const val EXTRA_SENDER_NAME = "sender_name"
        private const val EXTRA_EMOJI_URL = "emoji_url"

        /**
         * Pops (or bumps the badge on) the floating chat head.
         * @return false if the bubble could not be shown (missing overlay permission or a
         *   background service-start restriction) so the caller can fall back to a
         *   notification. Failures are logged — never swallowed silently.
         */
        fun show(context: Context, connectionId: String, senderName: String, emojiUrl: String = ""): Boolean {
            if (!android.provider.Settings.canDrawOverlays(context)) {
                android.util.Log.w("ChatHead", "no overlay permission — falling back to notification")
                return false
            }
            return runCatching {
                context.startService(
                    Intent(context, ChatHeadService::class.java).apply {
                        putExtra(EXTRA_CONNECTION_ID, connectionId)
                        putExtra(EXTRA_SENDER_NAME, senderName)
                        putExtra(EXTRA_EMOJI_URL, emojiUrl)
                    }
                )
                true
            }.getOrElse {
                android.util.Log.e("ChatHead", "startService failed: ${it.javaClass.simpleName}: ${it.message}")
                false
            }
        }

        fun hide(context: Context) {
            runCatching { context.stopService(Intent(context, ChatHeadService::class.java)) }
        }
    }
}
