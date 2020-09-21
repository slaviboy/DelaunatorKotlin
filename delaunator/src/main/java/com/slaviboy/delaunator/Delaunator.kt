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
package com.slaviboy.delaunator

import com.slaviboy.graphics.PointD

/**
 *  Class for generating Delaunay triangulation out of 2D points
 *  https://en.wikipedia.org/wiki/Delaunay_triangulation
 *
 *  Inspired by the JS implementation (this code was ported from Mapbox's Delaunator project)
 *  https://github.com/mapbox/delaunator
 */
class Delaunator(vararg var coordinates: Double) {

    constructor(vararg coordinates: Float) : this(*coordinates.toDoubleArray())
    constructor(coordinates: ArrayList<Float>) : this(*coordinates.toDoubleArray())

    internal lateinit var trianglesTemp: IntArray
    internal lateinit var halfEdgesTemp: IntArray
    internal lateinit var hullPreviousTemp: IntArray     // edge to prev edge
    internal lateinit var hullNextTemp: IntArray         // edge to next edge
    internal lateinit var hullTrianglesTemp: IntArray    // edge to adjacent triangle
    internal lateinit var hullHashTemp: IntArray         // angular edge hash
    internal lateinit var idsTemp: IntArray
    internal lateinit var distanceTemp: DoubleArray
    internal var hashSizeTemp: Int
    internal var hullStartTemp: Int
    internal var trianglesLen: Int
    internal var pointTemp: PointD

    internal var cX: Double
    internal var cY: Double

    lateinit var triangles: IntArray
    lateinit var halfEdges: IntArray
    lateinit var hull: IntArray

    init {

        cX = 0.0
        cY = 0.0
        hashSizeTemp = 0
        hullStartTemp = 0
        trianglesLen = 0
        pointTemp = PointD()

        init()
        update()
    }

    internal fun init() {

        val n = coordinates.size / 2

        // arrays that will store the triangulation graph
        val maxTriangles = Math.max(2 * n - 5, 0)
        trianglesTemp = IntArray(maxTriangles * 3)
        halfEdgesTemp = IntArray(maxTriangles * 3)

        // temporary arrays for tracking the edges of the advancing convex hull
        hashSizeTemp = Math.ceil(Math.sqrt(n.toDouble())).toInt()
        hullPreviousTemp = IntArray(n)
        hullNextTemp = IntArray(n)
        hullTrianglesTemp = IntArray(n)
        hullHashTemp = IntArray(hashSizeTemp)
        hullHashTemp.fill(-1, 0, hashSizeTemp)

        // temporary arrays for sorting points
        idsTemp = IntArray(n)
        distanceTemp = DoubleArray(n)
    }

    /**
     * Updates the triangulation if you modified delaunay.coordinates values in place, avoiding expensive memory allocations.
     * Useful for iterative relaxation algorithms such as Lloyd's.
     */
    fun update() {

        val n = coordinates.size / 2

        // populate an array of point indices, calculate input data boundary box
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (i: Int in 0 until n) {
            val x = coordinates[2 * i]
            val y = coordinates[2 * i + 1]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            this.idsTemp[i] = i
        }

        val cx: Double = (minX + maxX) / 2.0
        val cy: Double = (minY + maxY) / 2.0
        var minDist = Double.POSITIVE_INFINITY
        var i0 = 0
        var i1 = 0
        var i2 = 0

        // pick a seed point close to the center
        for (i: Int in 0 until n) {
            val d: Double = distance(cx, cy, coordinates[2 * i], coordinates[2 * i + 1])
            if (d < minDist) {
                i0 = i
                minDist = d
            }
        }

        val i0x = coordinates[2 * i0]
        val i0y = coordinates[2 * i0 + 1]
        minDist = Double.POSITIVE_INFINITY

        // find the point closest to the seed
        for (i: Int in 0 until n) {
            if (i == i0) {
                continue
            }
            val d = distance(i0x, i0y, coordinates[2 * i], coordinates[2 * i + 1])
            if (d < minDist && d > 0) {
                i1 = i
                minDist = d
            }
        }

        var i1x = coordinates[2 * i1]
        var i1y = coordinates[2 * i1 + 1]
        var minRadius = Double.POSITIVE_INFINITY

        // find the third point which forms the smallest circumcircle with the first two
        for (i: Int in 0 until n) {
            if (i == i0 || i == i1) {
                continue
            }
            val r: Double = circumradius(i0x, i0y, i1x, i1y, coordinates[2 * i], coordinates[2 * i + 1])
            if (r < minRadius) {
                i2 = i
                minRadius = r
            }
        }
        var i2x = coordinates[2 * i2]
        var i2y = coordinates[2 * i2 + 1]

        if (minRadius == Double.POSITIVE_INFINITY) {
            // order collinear points by dx (or dy if all x are identical)
            // and return the list as a hull
            for (i: Int in 0 until n) {
                distanceTemp[i] = if ((coordinates[2 * i] - coordinates[0]) != 0.0) {
                    (coordinates[2 * i] - coordinates[0])
                } else {
                    (coordinates[2 * i + 1] - coordinates[1])
                }
            }

            quicksort(idsTemp, distanceTemp, 0, n - 1)

            val hullTemp = IntArray(n)
            var j = 0
            var d0 = Double.NEGATIVE_INFINITY
            for (i: Int in 0 until n) {
                val id = idsTemp[i]
                if (distanceTemp[id] > d0) {
                    hullTemp[j++] = idsTemp[i]
                    d0 = distanceTemp[id]
                }
            }

            hull = hullTemp.copyOfRange(0, j)
            triangles = IntArray(0)
            halfEdges = IntArray(0)
            return
        }

        // swap the order of the seed points for counter-clockwise orientation
        if (orient(i0x, i0y, i1x, i1y, i2x, i2y)) {
            val i = i1
            val x = i1x
            val y = i1y
            i1 = i2
            i1x = i2x
            i1y = i2y
            i2 = i
            i2x = x
            i2y = y
        }

        circumcenter(i0x, i0y, i1x, i1y, i2x, i2y, pointTemp)
        this.cX = pointTemp.x
        this.cY = pointTemp.y

        for (i: Int in 0 until n) {
            distanceTemp[i] = distance(coordinates[2 * i], coordinates[2 * i + 1], pointTemp.x, pointTemp.y)
        }

        // sort the points by distance from the seed triangle circumcenter
        quicksort(idsTemp, distanceTemp, 0, n - 1)

        // set up the seed triangle as the starting hull
        hullStartTemp = i0
        var hullSize = 3

        hullNextTemp[i0] = i1
        hullPreviousTemp[i2] = i1
        hullNextTemp[i1] = i2
        hullPreviousTemp[i0] = i2
        hullNextTemp[i2] = i0
        hullPreviousTemp[i1] = i0

        hullTrianglesTemp[i0] = 0
        hullTrianglesTemp[i1] = 1
        hullTrianglesTemp[i2] = 2

        hullHashTemp.fill(-1)
        hullHashTemp[hashKey(i0x, i0y)] = i0
        hullHashTemp[hashKey(i1x, i1y)] = i1
        hullHashTemp[hashKey(i2x, i2y)] = i2

        trianglesLen = 0
        addTriangle(i0, i1, i2, -1, -1, -1)

        var xp = 0.0
        var yp = 0.0
        for (k in idsTemp.indices) {
            val i = idsTemp[k]
            val x = coordinates[2 * i]
            val y = coordinates[2 * i + 1]

            // skip near-duplicate points
            if (k > 0 && Math.abs(x - xp) <= EPSILON && Math.abs(y - yp) <= EPSILON) {
                continue
            }
            xp = x
            yp = y

            // skip seed triangle points
            if (i == i0 || i == i1 || i == i2) {
                continue
            }

            // find a visible edge on the convex hull using edge hash
            var start = 0
            val key = hashKey(x, y)
            for (j in 0 until hashSizeTemp) {
                start = hullHashTemp[(key + j) % hashSizeTemp]
                if (start != -1 && start != hullNextTemp[start]) {
                    break
                }
            }

            start = hullPreviousTemp[start]
            var e = start
            var q = hullNextTemp[e]
            while (!orient(x, y, coordinates[2 * e], coordinates[2 * e + 1], coordinates[2 * q], coordinates[2 * q + 1])
            ) {
                e = q
                if (e == start) {
                    e = -1
                    break
                }
                q = hullNextTemp[e]
            }

            // likely a near-duplicate point- skip it
            if (e == -1) {
                continue
            }

            // add the first triangle from the point
            var t = addTriangle(e, i, hullNextTemp[e], -1, -1, hullTrianglesTemp[e])

            // recursively flip triangles from the point until they satisfy the Delaunay condition
            hullTrianglesTemp[i] = legalize(t + 2)
            hullTrianglesTemp[e] = t // keep track of boundary triangles on the hull
            hullSize++

            // walk forward through the hull, adding more triangles and flipping recursively
            var n = hullNextTemp[e]
            q = hullNextTemp[n]
            while (orient(x, y, coordinates[2 * n], coordinates[2 * n + 1], coordinates[2 * q], coordinates[2 * q + 1])
            ) {
                t = addTriangle(n, i, q, hullTrianglesTemp[i], -1, hullTrianglesTemp[n])
                hullTrianglesTemp[i] = legalize(t + 2)
                hullNextTemp[n] = n // mark as removed
                hullSize--
                n = q
                q = hullNextTemp[n]
            }

            // walk backward from the other side, adding more triangles and flipping
            if (e == start) {
                q = hullPreviousTemp[e]
                while (orient(x, y, coordinates[2 * q], coordinates[2 * q + 1], coordinates[2 * e], coordinates[2 * e + 1])
                ) {
                    t = addTriangle(q, i, e, -1, hullTrianglesTemp[e], hullTrianglesTemp[q])
                    legalize(t + 2)
                    hullTrianglesTemp[q] = t
                    hullNextTemp[e] = e // mark as removed
                    hullSize--
                    e = q
                    q = hullPreviousTemp[e]
                }
            }

            // update the hull indices
            hullStartTemp = e
            hullPreviousTemp[i] = e
            hullNextTemp[e] = i
            hullPreviousTemp[n] = i
            hullNextTemp[i] = n

            // save the two new edges in the hash table
            hullHashTemp[hashKey(x, y)] = i
            hullHashTemp[hashKey(coordinates[2 * e], coordinates[2 * e + 1])] = e
        }

        hull = IntArray(hullSize)
        var e = hullStartTemp
        for (i in 0 until hullSize) {
            hull[i] = e
            e = hullNextTemp[e]
        }

        // trim typed triangle mesh arrays
        triangles = trianglesTemp.copyOfRange(0, trianglesLen)
        halfEdges = halfEdgesTemp.copyOfRange(0, trianglesLen)
    }

    internal fun hashKey(x: Double, y: Double): Int {
        return (Math.floor(pseudoAngle(x - cX, y - cY) * hashSizeTemp) % hashSizeTemp).toInt()
    }

    internal fun addTriangle(i0: Int, i1: Int, i2: Int, a: Int, b: Int, c: Int): Int {
        val t = trianglesLen

        trianglesTemp[t] = i0
        trianglesTemp[t + 1] = i1
        trianglesTemp[t + 2] = i2

        link(t, a)
        link(t + 1, b)
        link(t + 2, c)

        trianglesLen += 3

        return t
    }

    internal fun link(a: Int, b: Int) {
        halfEdgesTemp[a] = b
        if (b != -1) halfEdgesTemp[b] = a
    }

    internal fun legalize(_a: Int): Int {

        var a = _a
        var i = 0
        var ar = 0

        // recursion eliminated with a fixed-size stack
        while (true) {
            val b = halfEdgesTemp[a]

            /* if the pair of triangles doesn't satisfy the Delaunay condition
             * (p1 is inside the circumcircle of [p0, pl, pr]), flip them,
             * then do the same check/flip recursively for the new pair of triangles
             *
             *           pl                    pl
             *          /||\                  /  \
             *       al/ || \bl            al/    \a
             *        /  ||  \              /      \
             *       /  a||b  \    flip    /___ar___\
             *     p0\   ||   /p1   =>   p0\---bl---/p1
             *        \  ||  /              \      /
             *       ar\ || /br             b\    /br
             *          \||/                  \  /
             *           pr                    pr
             */
            val a0 = a - a % 3
            ar = a0 + (a + 2) % 3

            if (b == -1) {
                // convex hull edge
                if (i == 0) {
                    break
                }
                a = EDGE_STACK[--i]
                continue
            }

            val b0 = b - b % 3
            val al = a0 + (a + 1) % 3
            val bl = b0 + (b + 2) % 3

            val p0 = trianglesTemp[ar]
            val pr = trianglesTemp[a]
            val pl = trianglesTemp[al]
            val p1 = trianglesTemp[bl]

            val illegal = inCircle(
                coordinates[2 * p0], coordinates[2 * p0 + 1],
                coordinates[2 * pr], coordinates[2 * pr + 1],
                coordinates[2 * pl], coordinates[2 * pl + 1],
                coordinates[2 * p1], coordinates[2 * p1 + 1]
            )

            if (illegal) {
                trianglesTemp[a] = p1
                trianglesTemp[b] = p0

                val hbl = halfEdgesTemp[bl]

                // edge swapped on the other side of the hull (rare) fix the halfedge reference
                if (hbl == -1) {
                    var e = hullStartTemp
                    do {
                        if (hullTrianglesTemp[e] == bl) {
                            hullTrianglesTemp[e] = a
                            break
                        }
                        e = hullPreviousTemp[e]
                    } while (e != hullStartTemp)
                }
                link(a, hbl)
                link(b, halfEdgesTemp[ar])
                link(ar, bl)

                val br = b0 + (b + 1) % 3

                // don't worry about hitting the cap: it can only happen on extremely degenerate input
                if (i < EDGE_STACK.size) {
                    EDGE_STACK[i++] = br
                }
            } else {
                if (i == 0) {
                    break
                }
                a = EDGE_STACK[--i]
            }
        }

        return ar
    }

    // static methods and variable
    companion object {

        val EPSILON: Double = Math.pow(2.0, -52.0)

        val EDGE_STACK = IntArray(512)

        /**
         * Converts FloatArray to DoubleArray
         */
        fun FloatArray.toDoubleArray(): DoubleArray {
            val doubleArray = DoubleArray(this.size)
            for (i in this.indices) {
                doubleArray[i] = this[i].toDouble()
            }
            return doubleArray
        }

        /**
         * Converts ArrayList<Float> to DoubleArray
         */
        fun ArrayList<Float>.toDoubleArray(): DoubleArray {
            val doubleArray = DoubleArray(this.size)
            for (i in this.indices) {
                doubleArray[i] = this[i].toDouble()
            }
            return doubleArray
        }

        fun from(points: ArrayList<PointD>): Delaunator {
            val coordinates = DoubleArray(points.size * 2)
            for (i in points.indices) {
                coordinates[i * 2] = points[i].x
                coordinates[i * 2 + 1] = points[i].y
            }
            return Delaunator(*coordinates)
        }

        /**
         * Quick sort the ids, used for sort the points by distance from the seed triangle circumcenter
         * @param ids ids to sort
         * @param distance distance from the seed triangle circumcenter
         */
        fun quicksort(ids: IntArray, distance: DoubleArray, left: Int, right: Int) {
            if (right - left <= 20) {
                for (i: Int in (left + 1) until right + 1) {
                    val temp = ids[i]
                    val tempDistance = distance[temp]
                    var j = i - 1
                    while (j >= left && distance[ids[j]] > tempDistance) {
                        ids[j + 1] = ids[j--]
                    }
                    ids[j + 1] = temp
                }
            } else {
                val median = (left + right) shr 1
                var i = left + 1
                var j = right
                swap(ids, median, i)
                if (distance[ids[left]] > distance[ids[right]]) swap(ids, left, right)
                if (distance[ids[i]] > distance[ids[right]]) swap(ids, i, right)
                if (distance[ids[left]] > distance[ids[i]]) swap(ids, left, i)

                val temp = ids[i]
                val tempDistance = distance[temp]
                while (true) {
                    do i++ while (distance[ids[i]] < tempDistance)
                    do j-- while (distance[ids[j]] > tempDistance)
                    if (j < i) break
                    swap(ids, i, j)
                }
                ids[left + 1] = ids[j]
                ids[j] = temp

                if (right - i + 1 >= j - left) {
                    quicksort(ids, distance, i, right)
                    quicksort(ids, distance, left, j - 1)
                } else {
                    quicksort(ids, distance, left, j - 1)
                    quicksort(ids, distance, i, right)
                }
            }
        }

        /**
         * Swap elements in array with given indices
         * @param array array whose elements will be swapped
         * @param i index of the first element
         * @param j index of the second element
         */
        fun swap(array: IntArray, i: Int, j: Int) {
            val temp = array[i]
            array[i] = array[j]
            array[j] = temp
        }

        /**
         * Monotonically increases with real angle, but doesn't need expensive trigonometry
         */
        fun pseudoAngle(dx: Double, dy: Double): Double {
            val p = dx / (Math.abs(dx) + Math.abs(dy))

            // [0..1]
            return if (dy > 0.0) {
                (3 - p) / 4.0
            } else {
                (1 + p) / 4.0
            }
        }

        /**
         * Represent the distance between two points, without the square root of both sides
         * @param ax first point x coordinate
         * @param ay first point y coordinate
         * @param ax second point x coordinate
         * @param by second point y coordinate
         */
        fun distance(ax: Double, ay: Double, bx: Double, by: Double): Double {
            val dx = ax - bx
            val dy = ay - by
            return dx * dx + dy * dy
        }

        /**
         * Method that returns the circumradius of the circle around a triangle with given points
         * @param ax x coordinate of the first triangle point
         * @param ay y coordinate of the first triangle point
         * @param bx x coordinate of the second triangle point
         * @param by y coordinate of the second triangle point
         * @param cx x coordinate of the third triangle point
         * @param cy y coordinate of the third triangle point
         */
        fun circumradius(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
            val dx = bx - ax
            val dy = by - ay
            val ex = cx - ax
            val ey = cy - ay

            val bl = dx * dx + dy * dy
            val cl = ex * ex + ey * ey
            val d = 0.5 / (dx * ey - dy * ex)

            val x = (ey * bl - dy * cl) * d
            val y = (dx * cl - ex * bl) * d

            return x * x + y * y
        }

        /**
         * More robust orientation test that's stable in a given triangle (to fix robustness issues)
         */
        fun orient(rx: Double, ry: Double, qx: Double, qy: Double, px: Double, py: Double): Boolean {
            val sign = when {
                orientIfSure(px, py, rx, ry, qx, qy) != 0.0 -> {
                    orientIfSure(px, py, rx, ry, qx, qy)
                }
                orientIfSure(rx, ry, qx, qy, px, py) != 0.0 -> {
                    orientIfSure(rx, ry, qx, qy, px, py)
                }
                else -> {
                    orientIfSure(qx, qy, px, py, rx, ry)
                }
            }
            return sign < 0.0
        }

        /**
         * Return 2d orientation sign if we're confident in it through J. Shewchuk's error bound check
         */
        fun orientIfSure(px: Double, py: Double, rx: Double, ry: Double, qx: Double, qy: Double): Double {
            val l = (ry - py) * (qx - px)
            val r = (rx - px) * (qy - py)
            return if (Math.abs(l - r) >= (3.3306690738754716e-16 * Math.abs(l + r))) {
                l - r
            } else {
                0.0
            }
        }

        /**
         * Method that returns the circumcenter of the circle around a triangle with given points
         * @param ax x coordinate of the first triangle point
         * @param ay y coordinate of the first triangle point
         * @param bx x coordinate of the second triangle point
         * @param by y coordinate of the second triangle point
         * @param cx x coordinate of the third triangle point
         * @param cy y coordinate of the third triangle point
         */
        fun circumcenter(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, p: PointD) {
            val dx = bx - ax
            val dy = by - ay
            val ex = cx - ax
            val ey = cy - ay

            val bl = dx * dx + dy * dy
            val cl = ex * ex + ey * ey
            val d = 0.5 / (dx * ey - dy * ex)

            p.x = ax + (ey * bl - dy * cl) * d
            p.y = ay + (dx * cl - ex * bl) * d
        }

        /**
         * Methods that checks if point P is inside the circumcircle of triangle ABC
         * @param ax x coordinate of the first triangle point
         * @param ay y coordinate of the first triangle point
         * @param bx x coordinate of the second triangle point
         * @param by y coordinate of the second triangle point
         * @param cx x coordinate of the third triangle point
         * @param cy y coordinate of the third triangle point
         * @param px x coordinate of the point that is checked
         * @param py y coordinate of the point that is checked
         */
        fun inCircle(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, px: Double, py: Double): Boolean {
            val dx = ax - px
            val dy = ay - py
            val ex = bx - px
            val ey = by - py
            val fx = cx - px
            val fy = cy - py

            val ap = dx * dx + dy * dy
            val bp = ex * ex + ey * ey
            val cp = fx * fx + fy * fy

            return dx * (ey * cp - bp * fy) - dy * (ex * cp - bp * fx) + ap * (ex * fy - ey * fx) < 0
        }
    }
}