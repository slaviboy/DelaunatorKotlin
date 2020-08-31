package com.slaviboy.delaunator

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Class tha holds the expected delaunator values for the test, that are extracted from the
 * json file './res/raw/values.json'
 */
class DelaunatorValues(
    var points: DoubleArray, var halfEdges: IntArray, val hull: IntArray, var triangles: IntArray,
    var distanceTemp: DoubleArray, var halfEdgesTemp: IntArray, var hullTrianglesTemp: IntArray,
    var trianglesTemp: IntArray, var hullHashTemp: IntArray, var hullNextTemp: IntArray,
    var hullPreviousTemp: IntArray, var idsTemp: IntArray,
    var hashSizeTemp: Int, var trianglesLen: Int, var hullStartTemp: Int
)

/**
 * Class the holds values for know issues in the past that are fixed, and are extracted from the
 * json file './res/raw/fixtures.json'
 */
class DelaunatorFixtures(
    var ukraine: DoubleArray, var robustness1: DoubleArray, var robustness2: DoubleArray,
    var issue13: DoubleArray, var issue43: DoubleArray, var issue44: DoubleArray,
    var issue11: DoubleArray, var issue24: DoubleArray,
)

@Config(sdk = [Build.VERSION_CODES.P])
@RunWith(RobolectricTestRunner::class)
class DelaunatorUnitTest {

    @Test
    fun DelaunatorTest() {

        val gson = Gson()
        val context: Context = ApplicationProvider.getApplicationContext()

        // load json file with expected test values
        val jsonDelaunatorValues = loadStringFromRawResource(context.resources, R.raw.values)
        val delaunatorValues: DelaunatorValues = gson.fromJson(jsonDelaunatorValues, DelaunatorValues::class.java)

        // load json file with fixtures input data
        val jsonDelaunatorFixtures = loadStringFromRawResource(context.resources, R.raw.fixtures)
        val delaunatorFixtures: DelaunatorFixtures = gson.fromJson(jsonDelaunatorFixtures, DelaunatorFixtures::class.java)

        CheckDelaunatorStaticMethods()
        CheckDelaunatorValues(delaunatorValues)
        CheckDelaunatorFixtures(delaunatorFixtures)
    }

    /**
     * Check delaunator fixtures for know issues and test for correct triangulation
     * @param delaunatorFixtures input values for the know issues
     */
    fun CheckDelaunatorFixtures(delaunatorFixtures: DelaunatorFixtures) {

        val points = delaunatorFixtures.ukraine

        //region produces correct triangulation
        validate(points)
        //endregion

        //region produces correct triangulation after modifying coordinates in place
        val d = Delaunator(*points.clone())

        validate(points, d)
        assertThat(d.trianglesLen).isEqualTo(5133)

        val p = doubleArrayOf(80.0, 220.0)
        d.coordinates[0] = p[0]
        d.coordinates[1] = p[1]
        val newPoints = p + points.slice(2 until points.size)

        d.update()
        validate(newPoints, d)
        assertThat(d.trianglesLen).isEqualTo(5139)
        //endregion

        //region known issues
        validate(delaunatorFixtures.issue11)
        validate(delaunatorFixtures.issue13)
        validate(delaunatorFixtures.issue24)
        validate(delaunatorFixtures.issue43)
        validate(delaunatorFixtures.issue44)
        //endregion

        //region test robustness
        validate(delaunatorFixtures.robustness1)
        validate(delaunatorFixtures.robustness1.map { it / 1e9f }.toDoubleArray())
        validate(delaunatorFixtures.robustness1.map { it / 100f }.toDoubleArray())
        validate(delaunatorFixtures.robustness1.map { it * 100f }.toDoubleArray())
        validate(delaunatorFixtures.robustness1.map { it * 1e9f }.toDoubleArray())
        validate(delaunatorFixtures.robustness2.slice(0 until 100).toDoubleArray())
        validate(delaunatorFixtures.robustness2)
        //endregion

    }

    fun CheckDelaunatorValues(delaunatorValues: DelaunatorValues) {

        val delaunator = Delaunator(*delaunatorValues.points)

        // public properties
        assertThat(delaunator.coordinates).isEqualTo(delaunatorValues.points)
        assertThat(delaunator.halfEdges).isEqualTo(delaunatorValues.halfEdges)
        assertThat(delaunator.hull).isEqualTo(delaunatorValues.hull)
        assertThat(delaunator.triangles).isEqualTo(delaunatorValues.triangles)

        // internal properties
        assertThat(delaunator.distanceTemp).isEqualTo(delaunatorValues.distanceTemp)
        assertThat(delaunator.halfEdgesTemp).isEqualTo(delaunatorValues.halfEdgesTemp)
        assertThat(delaunator.hullTrianglesTemp).isEqualTo(delaunatorValues.hullTrianglesTemp)
        assertThat(delaunator.trianglesTemp).isEqualTo(delaunatorValues.trianglesTemp)
        assertThat(delaunator.trianglesTemp).isEqualTo(delaunatorValues.trianglesTemp)
        assertThat(delaunator.hullHashTemp).isEqualTo(delaunatorValues.hullHashTemp)
        assertThat(delaunator.hullNextTemp).isEqualTo(delaunatorValues.hullNextTemp)
        assertThat(delaunator.hullPreviousTemp).isEqualTo(delaunatorValues.hullPreviousTemp)
        assertThat(delaunator.idsTemp).isEqualTo(delaunatorValues.idsTemp)
        assertThat(delaunator.hashSizeTemp).isEqualTo(delaunatorValues.hashSizeTemp)
        assertThat(delaunator.trianglesLen).isEqualTo(delaunatorValues.trianglesLen)
        assertThat(delaunator.hullStartTemp).isEqualTo(delaunatorValues.hullStartTemp)
    }

    fun CheckDelaunatorStaticMethods() {

        // test quick sort with less than 20 elements
        var ids = intArrayOf(0, 1, 2, 3, 4, 5)
        var distance = doubleArrayOf(
            1533.6224, 4151.932,
            1533.6224, 1533.6223,
            124768.3, 94816.07
        )
        Delaunator.quicksort(ids, distance, 0, 5)
        assertThat(ids).isEqualTo(intArrayOf(3, 0, 2, 1, 5, 4))

        // test quick sort with more than 20 elements
        ids = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
        distance = doubleArrayOf(
            161937.0, 143687.16,
            115131.09, 143376.16,
            7841.4062, 14617.137,
            122419.93, 20793.865,
            89126.195, 133176.06,
            7841.41, 60282.06,
            28699.441, 83708.664,
            159947.75, 88896.21,
            140156.69, 7841.4077,
            117781.96, 66338.305,
            117436.836, 113623.766
        )
        Delaunator.quicksort(ids, distance, 0, 21)
        assertThat(ids).isEqualTo(intArrayOf(4, 17, 10, 5, 7, 12, 11, 19, 13, 15, 8, 21, 2, 20, 18, 6, 9, 16, 3, 1, 14, 0))

        // test distance between two points
        val distanceTwoPoints = Delaunator.distance(3.0, 43.0, 214.0, 141.0)
        assertThat(distanceTwoPoints).isEqualTo(54125.0)

        // test circumradius of a triangle
        val circumradius = Delaunator.circumradius(0.0, 0.0, 132.0, 41.0, 53.0, 421.0)
        assertThat(circumradius).isEqualTo(45431.504410937196)

        // test circumcenter of a triangle
        val circumcenter = Delaunator.PointD()
        Delaunator.circumcenter(0.0, 0.0, 132.0, 41.0, 53.0, 421.0, circumcenter)
        assertThat(circumcenter).isEqualTo(Delaunator.PointD(6.19070581846102, 213.0567519991011))

        var isPointInCircumcircle = Delaunator.inCircle(0.0, 0.0, 100.0, 0.0, 50.0, 90.0, 150.0, 25.0)
        assertThat(isPointInCircumcircle).isEqualTo(true)

        isPointInCircumcircle = Delaunator.inCircle(0.0, 0.0, 100.0, 0.0, 50.0, 90.0, 100.0, 25.0)
        assertThat(isPointInCircumcircle).isEqualTo(false)
    }

    fun validate(coordinates: DoubleArray, delaunator: Delaunator = Delaunator(*coordinates)) {

        val points = ArrayList<Delaunator.PointD>()
        for (i in 0 until coordinates.size / 2) {
            points.add(Delaunator.PointD(coordinates[i * 2], coordinates[i * 2 + 1]))
        }

        validate(points, delaunator)
    }

    fun validate(points: ArrayList<Delaunator.PointD>, d: Delaunator = Delaunator.from(points)) {

        // check invalid halfedge connection
        for (i in d.halfEdges.indices) {
            val i2 = d.halfEdges[i]
            val isInvalidHalfedge = (i2 != -1 && d.halfEdges[i2] != i)
            assertThat(isInvalidHalfedge).isFalse()
        }

        // validate triangulation
        val hullAreas = DoubleArray(d.hull.size)
        var j = d.hull.size - 1
        for (i in d.hull.indices) {

            val x0 = points[d.hull[j]].x
            val y0 = points[d.hull[j]].y

            val x = points[d.hull[i]].x
            val y = points[d.hull[i]].y

            hullAreas[i] = (x - x0) * (y + y0)
            val isConvex = isConvex(
                points[d.hull[j]],
                points[d.hull[(j + 1) % d.hull.size]],
                points[d.hull[(j + 3) % d.hull.size]]
            )

            // check hull is not convex
            assertThat(isConvex).isTrue()

            j = i
        }

        // get array with the area for each triangle
        val triangleAreas = DoubleArray(d.triangles.size)
        for (i in 0 until d.triangles.size / 3) {

            val ax = points[d.triangles[i * 3]].x
            val ay = points[d.triangles[i * 3]].y

            val bx = points[d.triangles[i * 3 + 1]].x
            val by = points[d.triangles[i * 3 + 1]].y

            val cx = points[d.triangles[i * 3 + 2]].x
            val cy = points[d.triangles[i * 3 + 2]].y

            triangleAreas[i] = Math.abs((by - ay) * (cx - bx) - (bx - ax) * (cy - by))
        }

        // get triangles and hull area and make sure they match
        val hullArea = sum(hullAreas)
        val trianglesArea = sum(triangleAreas)
        val err = Math.abs((hullArea - trianglesArea) / hullArea)

        // triangulation is broken
        val isTriangulationBroken = err > Math.pow(2.0, -51.0)
        assertThat(isTriangulationBroken).isFalse()
    }

    fun orient(px: Double, py: Double, rx: Double, ry: Double, qx: Double, qy: Double): Double {
        val l = (ry - py) * (qx - px)
        val r = (rx - px) * (qy - py)
        return if (Math.abs(l - r) >= 3.3306690738754716e-16 * Math.abs(l + r)) l - r else 0.0
    }

    fun isConvex(r: Delaunator.PointD, q: Delaunator.PointD, p: Delaunator.PointD): Boolean {
        val orient1 = orient(p.x, p.y, r.x, r.y, q.x, q.y)
        val orient2 = orient(r.x, r.y, q.x, q.y, p.x, p.y)
        val orient3 = orient(q.x, q.y, p.x, p.y, r.x, r.y)

        val orient = when {
            orient1 != 0.0 -> orient1
            orient2 != 0.0 -> orient2
            orient3 != 0.0 -> orient3
            else -> 0.0
        }
        return orient >= 0.0
    }

    // accumulates less FP error
    fun sum(array: DoubleArray): Double {
        var sum = array[0]
        var err = 0.0
        for (i in 1 until array.size) {
            val k = array[i]
            val m = sum + k
            err += if (Math.abs(sum) >= Math.abs(k)) sum - m + k else k - m + sum
            sum = m
        }
        return (sum + err)
    }

    companion object {
        /**
         * Load string from the raw folder using a resource id of the given file.
         * @param resources resource from the context
         * @param resId resource id of the file
         */
        fun loadStringFromRawResource(resources: Resources, resId: Int): String {
            val rawResource = resources.openRawResource(resId)
            val content = streamToString(rawResource)
            try {
                rawResource.close()
            } catch (e: IOException) {
                throw e
            }
            return content
        }

        /**
         * Read the file from the raw folder using input stream
         */
        private fun streamToString(inputStream: InputStream): String {
            var l: String?
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            try {
                while (bufferedReader.readLine().also { l = it } != null) {
                    stringBuilder.append(l)
                }
            } catch (e: IOException) {
            }
            return stringBuilder.toString()
        }
    }
}