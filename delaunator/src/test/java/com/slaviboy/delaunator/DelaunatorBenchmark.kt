package com.slaviboy.delaunator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * This test will fail if you are using a slower processor, than the one used in this test:
 *  = AMD Ryzen5 3600 CPU 3.60GHz, 1 CPU, 6 physical and 12 logical cores
 */
class DelaunatorBenchmark {

    @Test
    fun MainTest() {

        /**
         *  ------------------------------------------------------------------------------------
         *  | Tested on [AMD Ryzen5 3600 CPU 3.60GHz, 1 CPU, 6 physical and 12 logical cores] |
         *  -----------------------------------------------------------------------------------
         *  |     uniform     |      100k: [56ms-75ms]      |    1million: [860ms-990ms]      |
         *  |       grid      |      100k: [45ms-78ms]      |    1million: [612ms-630ms]      |
         *  |     gaussian    |      100k: [53ms-70ms]      |    1million: [848ms-922ms]      |
         *  |    degenerate   |      100k: [63ms-160ms]     |    1million: [988ms-1300ms]???  |
         *  -----------------------------------------------------------------------------------
         */
        val expectedDurations = doubleArrayOf(

            // uniform
            75.0 + 10.0,
            990.0 + 100.0,

            // grid
            78.0 + 10.0,
            630.0 + 100.0,

            // gaussian
            70.0 + 10.0,
            922.0 + 100.0,

            // degenerate
            160.0 + 10.0,
            1300.0 + 100.0
        )

        val counts: ArrayList<Int> = arrayListOf(100000, 1000000)

        // warm-ups before the initial test
        Delaunator(*uniform(counts[0]))
        Delaunator(*grid(counts[1]))

        for (k in 0 until 4) {
            for (i in counts.indices) {
                val count = counts[i]

                val points = when (k) {
                    0 -> {
                        uniform(count)
                    }
                    1 -> {
                        grid(count)
                    }
                    2 -> {
                        gaussian(count)
                    }
                    else -> {
                        degenerate(count)
                    }
                }

                val start = System.nanoTime()
                Delaunator(*points)
                val end = System.nanoTime()
                val duration = (end - start) / 1000000.0
                val expectedDuration = expectedDurations[k * 2 + i]
                assertThat(duration).isLessThan(expectedDuration)
            }
        }
    }

    fun uniform(count: Int): DoubleArray {
        val points = DoubleArray(count * 2)
        for (i in 0 until count) {
            points[i * 2] = (Math.random() * 1e3)
            points[i * 2 + 1] = (Math.random() * 1e3)
        }
        return points
    }

    fun grid(count: Int): DoubleArray {
        val size = Math.sqrt(count.toDouble()).toInt()
        val points = DoubleArray(size * size * 2)
        var count = 0
        for (i in 0 until size) {
            for (j in 0 until size) {
                points[count] = i.toDouble()
                points[count + 1] = j.toDouble()
                count += 2
            }
        }
        return points
    }

    fun gaussian(count: Int): DoubleArray {
        val points = DoubleArray(count * 2)
        for (i in 0 until count) {
            points[i * 2] = (pseudoNormal() * 1e3)
            points[i * 2 + 1] = (pseudoNormal() * 1e3)
        }
        return points
    }

    fun degenerate(count: Int): DoubleArray {
        val points = DoubleArray(count * 2 + 2)
        points[0] = 0.0
        points[1] = 0.0
        for (i in 1..count) {
            val angle = (2.0 * Math.PI * i) / count
            points[i * 2] = (1e10 * Math.sin(angle))
            points[i * 2 + 1] = (1e10 * Math.cos(angle))
        }
        return points
    }

    fun pseudoNormal(): Double {
        val v = Math.random() + Math.random() + Math.random() + Math.random() + Math.random() + Math.random()
        return Math.min(0.5 * (v - 3) / 3, 1.0)
    }
}