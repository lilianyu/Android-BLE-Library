package com.techvll.android.detector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.techvll.android.detector.BuildConfig
import com.techvll.android.detector.databinding.ActivityAboutBinding
import java.io.File
import java.io.IOException
import java.util.Date


class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)

        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "关于"

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.versionNumber.text = "当前版本号：${versionName}"
        binding.tagContact.setOnClickListener {
            val uri = "tel:${binding.phoneNumber.text}"
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse(uri)
            startActivity(intent)
        }

        binding.tagSubmitLog.setOnClickListener {
            try {
                externalCacheDir?.let {
                    val name = android.text.format.DateFormat.format("yyyy-MM-dd", Date())
                    val  file = File("${it.absolutePath}/${name}")

                    val contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".FileProvider", file)

                    if (contentUri != null) {
                        var shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        shareIntent.type = "text/plain"
                        /** set the corresponding mime type of the file to be shared */
                        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)

                        startActivity(Intent.createChooser(shareIntent, "Share to"))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

}