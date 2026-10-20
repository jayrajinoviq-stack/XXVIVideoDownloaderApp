package com.design.home.views

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.design.home.R
import kotlin.math.abs

class MyAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var longPressHandler = Handler()
    var actionDownCoords = PointF()
    var currentCoords = PointF()
    private var actionDownMS = 0L
    val moveGestureThreshold = resources.getDimension(R.dimen.move_gesture_threshold).toInt() / 4
    var hasLongPressed = false
    var isHoldTriggered = false
    var ignoreTouches = false
    var longPressListener: ((x: Float, y: Float) -> Unit)? = null
    var dragListener: ((event: MotionEvent) -> Unit)? = null
    var onIgnoreInterceptedListener: (() -> Unit)? = null       // let the home grid react on swallowed clicks, for example by hiding the widget resize frame

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            currentCoords.x = ev.rawX
            currentCoords.y = ev.rawY
            if (isHoldTriggered) {
                dragListener?.invoke(ev)
            }
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev?.actionMasked == MotionEvent.ACTION_UP || ev?.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetTouches()
        }
        return handled
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches) {
            onIgnoreInterceptedListener?.invoke()
            return true
        }
        val handled = super.onTouchEvent(event)
        if (event?.actionMasked == MotionEvent.ACTION_UP || event?.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetTouches()
        }
        return handled || isHoldTriggered
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches || event == null) {
            return true
        }

        if (isHoldTriggered) {
            return true
        }

        if (hasLongPressed) {
            hasLongPressed = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isHoldTriggered = false
                hasLongPressed = false
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                actionDownMS = System.currentTimeMillis()
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                if (!isHoldTriggered && (abs(actionDownCoords.x - currentCoords.x) > moveGestureThreshold || abs(actionDownCoords.y - currentCoords.y) > moveGestureThreshold)) {
                    resetTouches()
                    return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouches()
            }
        }

        return false
    }

    private val longPressRunnable = Runnable {
        if (abs(actionDownCoords.x - currentCoords.x) < moveGestureThreshold && abs(actionDownCoords.y - currentCoords.y) < moveGestureThreshold) {
            longPressHandler.removeCallbacksAndMessages(null)
            hasLongPressed = true
            isHoldTriggered = true

            // Cancel child views' click state so the widget's PendingIntent never triggers on release
            val cancelEvent = MotionEvent.obtain(
                actionDownMS,
                System.currentTimeMillis(),
                MotionEvent.ACTION_CANCEL,
                currentCoords.x,
                currentCoords.y,
                0
            )
            super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()

            longPressListener?.invoke(actionDownCoords.x, actionDownCoords.y)
        }
    }

    fun resetTouches() {
        longPressHandler.removeCallbacksAndMessages(null)
        hasLongPressed = false
        isHoldTriggered = false
    }

    private fun hasFingerMoved(x: Float, y: Float) =
        ((abs(actionDownCoords.x - x) > moveGestureThreshold) || (abs(actionDownCoords.y - y) > moveGestureThreshold))
}
