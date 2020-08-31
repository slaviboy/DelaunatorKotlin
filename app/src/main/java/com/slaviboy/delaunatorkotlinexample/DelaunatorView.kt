/*
* Copyright (C) 2020 Stanislav Georgiev
* https://github.com/slaviboy
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.slaviboy.delaunatorkotlinexample

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.google.gson.Gson
import com.slaviboy.delaunator.Delaunator
import com.slaviboy.delaunatorkotlinexample.MainActivity.Companion.loadStringFromRawResource
import com.slaviboy.matrixgesturedetector.MatrixGestureDetector

/**
 * Simple delaunator view, that demonstrates the use of the Delaunator class,
 * and how to use it to draw delaunay triangulation.
 */
class DelaunatorView : View {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var points: FloatArray                                      // original points without transformations applied
    var transformedPoints: FloatArray                           // transformed points with applied transformation from the gesture matrix
    var delaunator: Delaunator                                  // delaunator object for generating delaunay triangulation
    var bound: RectF                                            // bound of the shape formed by (min,max) position of the points
    var isFingerMoved: Boolean                                  // whether finger is moved after it was pressed down, to know if click was made and add new point to the delaunator
    var touchDownTime: Long                                     // the initial time when the finger is pressed down, to determine if 'click' or 'hold down' was made
    lateinit var matrixGestureDetector: MatrixGestureDetector   // gesture detector for applying transformations: rotate, scale and translation

    // drawing variables path and paint
    val path: Path = Path()
    val paint: Paint = Paint().apply {
        isAntiAlias = true
    }

    class DelaunatorCoordinates(var coordinates: FloatArray)

    init {

        // use gson to extract the initial coordinates for the delaunator
        val gson = Gson()
        val jsonDelaunatorValues = loadStringFromRawResource(context.resources, R.raw.delaunator_coordinates)
        val delaunatorValues: DelaunatorCoordinates = gson.fromJson(jsonDelaunatorValues, DelaunatorCoordinates::class.java)

        // initialize points
        points = delaunatorValues.coordinates
        transformedPoints = FloatArray(points.size)
        isFingerMoved = false
        touchDownTime = 0L

        delaunator = Delaunator(*points)
        bound = RectF(
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        )

        // find the bound of the hull from all the points
        for (i in 0 until points.size / 2) {
            val x = points[i * 2]
            val y = points[i * 2 + 1]
            if (x < bound.left) bound.left = x
            if (x > bound.right) bound.right = x
            if (y < bound.top) bound.top = y
            if (y > bound.bottom) bound.bottom = y
        }

        this.afterMeasured {

            val boundMiddle = PointF(bound.centerX(), bound.centerY())

            // initialize gesture detector
            val scale = width / bound.width()
            val matrix = Matrix().apply {
                postTranslate(width / 2f - boundMiddle.x, height / 2f - boundMiddle.y)
                postScale(scale, scale, width / 2f, height / 2f)
            }
            matrixGestureDetector = MatrixGestureDetector(matrix)
            matrixGestureDetector.matrix.mapPoints(transformedPoints, points)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isFingerMoved = false
                touchDownTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val touchUpTime = System.currentTimeMillis()

                // if finger was not moved and hold than for longer than 250ms -> click event was made
                if (!isFingerMoved && touchUpTime - touchDownTime < 250) {
                    val fingerPoint = floatArrayOf(event.x, event.y)
                    val invertedMatrix = Matrix()
                    matrixGestureDetector.matrix.invert(invertedMatrix)
                    invertedMatrix.mapPoints(fingerPoint)

                    points += fingerPoint
                    transformedPoints = FloatArray(points.size)
                    delaunator = Delaunator(*points)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                isFingerMoved = true
            }
        }

        // apply transformation to the points on touch gestures
        matrixGestureDetector.onTouchEvent(event)
        matrixGestureDetector.matrix.mapPoints(transformedPoints, points)

        // request redrawing of the scene
        invalidate()

        return true
    }

    override fun onDraw(canvas: Canvas) {

        // draw edges with green color
        drawEdges(canvas, Color.rgb(0, 200, 0))

        // draw hull with red color
        drawHull(canvas, Color.RED)

        // draw points with black color
        drawPoints(canvas, Color.BLACK)
    }


    /**
     * Draw edges of the delaunay, with given stroke color
     * @param canvas canvas where the path of the edges will be drawn
     * @param strokeColor stroke color of the path
     */
    fun drawEdges(canvas: Canvas, strokeColor: Int) {

        val triangles = delaunator.triangles

        path.reset()
        for (i in triangles.indices step 3) {
            val p0 = triangles[i]
            val p1 = triangles[i + 1]
            val p2 = triangles[i + 2]
            path.moveTo(transformedPoints[p0 * 2], transformedPoints[p0 * 2 + 1])
            path.lineTo(transformedPoints[p1 * 2], transformedPoints[p1 * 2 + 1])
            path.lineTo(transformedPoints[p2 * 2], transformedPoints[p2 * 2 + 1])
            path.close()
        }
        paint.apply {
            strokeWidth = 1f
            style = Paint.Style.STROKE
            color = strokeColor
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Draw hull of the delaunay, with given stroke color
     * @param canvas canvas where the path of the hull will be drawn
     * @param strokeColor stroke color of the path
     */
    fun drawHull(canvas: Canvas, strokeColor: Int) {

        path.reset()
        var j = delaunator.hull[0]
        path.moveTo(transformedPoints[j * 2], transformedPoints[j * 2 + 1])
        for (i in delaunator.hull.indices) {
            j = delaunator.hull[i]
            path.lineTo(transformedPoints[j * 2], transformedPoints[j * 2 + 1])
        }
        path.close()
        paint.apply {
            strokeWidth = 2f
            color = strokeColor
        }
        canvas.drawPath(path, paint)
    }

    fun drawPoints(canvas: Canvas, fillColor: Int) {

        paint.apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        val circleRadius = 1.5f
        for (i in 0 until transformedPoints.size / 2) {
            canvas.drawCircle(
                transformedPoints[i * 2] - circleRadius,
                transformedPoints[i * 2 + 1] - circleRadius,
                circleRadius,
                paint
            )
        }
    }

    companion object {

        /**
         * Inline function that is called, when the final measurement is made and
         * the view is about to be draw.
         */
        inline fun View.afterMeasured(crossinline function: View.() -> Unit) {
            viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (measuredWidth > 0 && measuredHeight > 0) {
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        function()
                    }
                }
            })
        }
    }
}