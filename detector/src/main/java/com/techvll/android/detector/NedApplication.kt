package com.techvll.android.detector

import android.app.Application
import android.content.Intent
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.ConsolePrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.elvishew.xlog.printer.file.writer.SimpleWriter
import com.liulishuo.filedownloader.FileDownloader
import java.io.*


class NedApplication: Application() {

    companion object {
        const val FILE_PROVIDER_AUTHORITY = "no.nordicsemi.android.ble.ble_gatt_client.fileprovider"

        const val MAX_TIME: Long = 7 * 24 * 60 * 60 * 1000
    }

    override fun onCreate() {
        super.onCreate()

        FileDownloader.setup(applicationContext)


        val androidPrinter: Printer =
            AndroidPrinter(true) // Printer that print the log using android.util.Log

        val consolePrinter: Printer =
            ConsolePrinter() // Printer that print the log to console using System.out

        val filePrinter: Printer =
            FilePrinter.Builder(externalCacheDir?.absolutePath) // Specify the directory path of log file(s)
                .fileNameGenerator(DateFileNameGenerator()) // Default: ChangelessFileNameGenerator("log")
                .backupStrategy(NeverBackupStrategy()) // Default: FileSizeBackupStrategy(1024 * 1024)
                .cleanStrategy(FileLastModifiedCleanStrategy(MAX_TIME)) // Default: NeverCleanStrategy()
                .flattener(ClassicFlattener()) // Default: DefaultFlattener
                .writer(SimpleWriter()) // Default: SimpleWriter
                .build()

        XLog.init(                                                 // Initialize XLog
//            config,                                                // Specify the log configuration, if not specified, will use new LogConfiguration.Builder().build()
            androidPrinter,                                        // Specify printers, if no printer is specified, AndroidPrinter(for Android)/ConsolePrinter(for java) will be used.
            consolePrinter,
            filePrinter);


        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, throwable: Throwable ->
//            try {
//                 externalCacheDir?.let {
//                     val  file = File(it.absolutePath + "/ned_log.txt")
//                     file.delete()
//                     file.createNewFile()
//                     val cmd = "logcat -v -d -r 500 -f ${file.absolutePath}"
//                     Runtime.getRuntime().exec(cmd)
//
//                     val sw = StringWriter()
//                     throwable.printStackTrace(PrintWriter(sw))
//                     file.appendText(sw.toString())
//                     sw.close()
//
//                     val contentUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, file)
//                     /** FILE_PROVIDER_AUTHORITY - "applicationId" + ".fileprovider" */
//
//                     if (contentUri != null) {
//                         var shareIntent = Intent(Intent.ACTION_SEND)
//                         shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                         shareIntent.type = "text/plain"
//                         /** set the corresponding mime type of the file to be shared */
//                         shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
//
//                         startActivity(Intent.createChooser(shareIntent, "Share to"))
//                     }
//                 }
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }

            XLog.e("crash", throwable)

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val intent = Intent(Intent.ACTION_SEND)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK // required when starting from Application
            intent.putExtra(Intent.EXTRA_TEXT, sw.toString())
            intent.type = "text/plain"
            startActivity(intent)

            oldHandler.uncaughtException(thread, throwable)
        }
    }
}