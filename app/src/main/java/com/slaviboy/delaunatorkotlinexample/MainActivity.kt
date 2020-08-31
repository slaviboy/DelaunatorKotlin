package com.slaviboy.delaunatorkotlinexample

import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import com.slaviboy.delaunatorkotlinexample.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        actionBar?.hide()
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
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