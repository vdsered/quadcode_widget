package com.example.customwidget

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.View
import java.lang.Math.min
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.util.DisplayMetrics
import androidx.core.animation.doOnEnd
import com.example.customwidget.RegularPolygonState.COLLAPSED
import com.example.customwidget.RegularPolygonState.EXPANDED
import java.lang.Math.max

enum class RegularPolygonState {
    EXPANDED,
    COLLAPSED
}

/**
 * Требуется ждать анимации многих ValueAnimator'ов, поэтому заводим одного слушателя с счетчиком,
 * который инвалидирует View, когда достигнем некоторого порогового значения (экономим на перерисовках)
 */
class MultipleAnimatorListener(private val limit: Int, private val invalidation: () -> Unit) {
    private var counter = 0

    fun increment() {
        if (counter == limit) {
            invalidation()
            counter = 0
        }
        ++counter
    }
}

/**
 * Вью с анимацией поворота/движения к центру и от него
 *
 * Может не работать, если включено энергосбережение или из-за настроек разработчика (особенность ValueAnimator)
 */
//TODO: Надо ли сохранять стейт этой View или нужны только анимации?
class RegularPolygonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr), AnimatorUpdateListener {
    private val Pi = Math.PI.toFloat()
    private var animatedValue: Float = 0f
    private var animator = ValueAnimator()
    private var actualIconXPositions: FloatArray = FloatArray(0)
    private var actualIconYPositions: FloatArray = FloatArray(0)
    private var xIconCenters: FloatArray = FloatArray(0)
    private var yIconCenters: FloatArray = FloatArray(0)
    //TODO: Нужна ли возможность подставлять другие иконки?
    private val racoon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.racoon)
    //Нужно отличать выбранную иконку, поэтому используем какую-то другую, чтобы обозначить ее
    private val capybara: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.capybara)
    private var iconSize: Int
    private var animationSet = AnimatorSet()
    private var expansionState: RegularPolygonState = EXPANDED
    private val rect = RectF()
    private var xAnimators: MutableList<ValueAnimator> = mutableListOf()
    private var yAnimators: MutableList<ValueAnimator> = mutableListOf()

    var selectItem: Int = -1
        set(value) {
            field = when {
                value < -1 -> {
                    -1
                }
                value > iconCount -> {
                    iconCount - 1
                }
                else -> {
                    value
                }
            }
            invalidate()
        }

    /**
     * Количество иконок (N из задания)
     */
    var iconCount: Int = 3
        set(value) {
            val correctedValue = max(3, value)
            field = correctedValue
            changeNumberOfIcons(correctedValue)
        }

    init {
        iconSize = convertDpToPixel(24)
        animator.doOnEnd {
            currentTotalAngle += animatedValue
            animatedValue = 0f
        }

        val attributes = getContext().obtainStyledAttributes(attrs, R.styleable.RegularPolygonView)
        try {
            iconCount = attributes.getInt(R.styleable.RegularPolygonView_num, 3)
        }
        finally {
            attributes.recycle()
        }
    }

    fun expand() {
        if (expansionState == EXPANDED) {
            return
        }

        expansionState = EXPANDED
        animationSet.end()
        repeat(iconCount) {
            xAnimators[it].setFloatValues(0f, xIconCenters[it])
        }
        repeat(iconCount) {
            yAnimators[it].setFloatValues(0f, yIconCenters[it])
        }
        animationSet.start()
    }

    fun collapse() {
        if (expansionState == COLLAPSED) {
            return
        }

        expansionState = COLLAPSED

        animationSet.end()
        repeat(iconCount) {
            xAnimators[it].setFloatValues(xIconCenters[it], 0f)
        }
        repeat(iconCount) {
            yAnimators[it].setFloatValues(yIconCenters[it], 0f)
        }
        animationSet.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateIconInfo()
    }

    fun rotate(angle: Float) {
        val radians = angle * Pi / 180
        if (expansionState == COLLAPSED) {
            return
        }
        animator.setFloatValues(0f, radians)
        animator.addUpdateListener(this)
        animator.duration = ANIMATION_DURATION
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val xCenters = actualIconXPositions
        val yCenters = actualIconYPositions
        for (i in 0 until iconCount) {
            val x = xCenters[i] + width/2
            val y = yCenters[i] + height/2
            rect.left = x - iconSize
            rect.right = x + iconSize
            rect.top = y - iconSize
            rect.bottom = y + iconSize

            if (i == selectItem) {
                canvas.drawBitmap(capybara, null, rect, null)
            }
            else {
                canvas.drawBitmap(racoon, null, rect, null)
            }
        }
    }

    private var previousAngle = 0.0f
    private var currentTotalAngle = 0.0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return onTouchEvent(event)
        if (animator.isRunning) {
            animator.end()
        }

        when(event.action) {
            ACTION_DOWN -> {
                previousAngle = computePolarAngle(x = event.x, y = event.y)
            }
            ACTION_MOVE -> {
                val angle = computePolarAngle(x = event.x, y = event.y)
                currentTotalAngle += angle - previousAngle
                updateIconInfo()
                previousAngle = angle
            }
        }
        return true
    }

    private fun computePolarAngle(x: Float, y: Float): Float {
        val centralizedX = x - width/2
        val centralizedY = y - height/2
        val angle = asin(centralizedY/sqrt(centralizedX*centralizedX + centralizedY*centralizedY))
        if (centralizedX > 0 && centralizedY > 0) {
            return angle
        }
        else if (centralizedX < 0 && centralizedY > 0) {
            return Pi - angle
        }
        else if (centralizedX < 0 && centralizedY < 0) {
            return Pi - angle
        }
        else if (centralizedX > 0 && centralizedY < 0) {
            return 2 * Pi + angle
        }
        return 0.0f
    }

    private fun updateIconInfo() {
        val angle = (2 * Pi / iconCount.toFloat())
        var currentItemAngle = currentTotalAngle + animatedValue
        val smallestSide = min(width, height)
        val r = smallestSide / 2 - iconSize * 2
        repeat(iconCount) {
            val x = cos(currentItemAngle) * r
            val y = sin(currentItemAngle) * r
            xIconCenters[it] = x
            yIconCenters[it] = y
            if (expansionState != COLLAPSED) {
                actualIconXPositions[it] = x
                actualIconYPositions[it] = y
            }
            else {
                actualIconXPositions[it] = 0f
                actualIconYPositions[it] = 0f
            }
            currentItemAngle += angle
        }

        invalidate()
    }

    private fun convertDpToPixel(dp: Int): Int {
        return (dp.toFloat() * context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT).toInt()
    }

    private fun changeNumberOfIcons(n: Int) {
        animationSet.pause()

        val invalidator = MultipleAnimatorListener(n) {
            invalidate()
        }
        animationSet = AnimatorSet()
        actualIconXPositions = FloatArray(iconCount)
        actualIconYPositions = FloatArray(iconCount)
        xIconCenters = FloatArray(iconCount)
        yIconCenters = FloatArray(iconCount)
        animationSet.duration = ANIMATION_DURATION
        xAnimators.clear()
        yAnimators.clear()
        repeat(iconCount) { i ->
            val xAnimator = ValueAnimator()
            xAnimator.addUpdateListener {
                invalidator.increment()
                actualIconXPositions[i] = it.animatedValue as Float
            }
            val yAnimator = ValueAnimator()
            yAnimator.addUpdateListener {
                invalidator.increment()
                actualIconYPositions[i] = it.animatedValue as Float
            }
            xAnimators.add(xAnimator)
            yAnimators.add(yAnimator)
        }

        val mut = xAnimators.toMutableList()
        mut.addAll(yAnimators)
        animationSet.playTogether(mut.toList())
        updateIconInfo()
    }

    override fun onAnimationUpdate(p0: ValueAnimator) {
        animatedValue = p0.animatedValue as Float
        updateIconInfo()
    }

    companion object {
        const val ANIMATION_DURATION = 5000L
    }
}