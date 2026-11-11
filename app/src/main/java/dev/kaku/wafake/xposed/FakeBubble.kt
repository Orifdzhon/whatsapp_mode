package dev.kaku.wafake.xposed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Outgoing bubble drawn entirely by the module.
 * Nothing here touches WhatsApp's message model or its databases.
 */
object FakeBubble {

    private val GREEN = Color.parseColor("#DCF8C6")
    private val GREEN_DARK = Color.parseColor("#C5E8AE")
    private val TIME_GRAY = Color.parseColor("#667781")

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun create(ctx: Context): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val bubble = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 10).toFloat()
                setColor(GREEN)
                setStroke(dp(ctx, 1), GREEN_DARK)
            }
            setPadding(dp(ctx, 9), dp(ctx, 6), dp(ctx, 9), dp(ctx, 5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(ctx, 4) }
        }

        val body = TextView(ctx).apply {
            setTextColor(Color.parseColor("#111B21"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            maxWidth = dp(ctx, 260)
            tag = "wafake_body"
        }

        val time = TextView(ctx).apply {
            setTextColor(TIME_GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.END
            tag = "wafake_time"
        }

        bubble.addView(body)
        bubble.addView(time)
        row.addView(bubble)
        return row
    }

    fun bind(root: LinearLayout, text: String, ts: Long) {
        val body = root.findViewWithTag<TextView>("wafake_body")
        val time = root.findViewWithTag<TextView>("wafake_time")

        body.text = text
        body.maxLines = Int.MAX_VALUE
        body.ellipsize = TextUtils.TruncateAt.END

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        time.text = fmt.format(Date(ts))
        time.textSize = 11f
    }
}
