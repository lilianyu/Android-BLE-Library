package no.nordicsemi.android.ble.trivia

import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.ble.trivia.navigation.ClientDestinations
import no.nordicsemi.android.ble.trivia.navigation.NavigationConst
import no.nordicsemi.android.ble.trivia.navigation.ServerDestinations
import no.nordicsemi.android.ble.trivia.navigation.StartScreenDestination
import no.nordicsemi.android.common.navigation.NavigationView
import no.nordicsemi.android.common.navigation.createSimpleDestination
import no.nordicsemi.android.common.theme.NordicActivity
import no.nordicsemi.android.common.theme.NordicTheme


@AndroidEntryPoint
class MainActivity : NordicActivity() {

    private val REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: Int = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NordicTheme {
                NavigationView(
                    destinations = StartScreenDestination
                            + ServerDestinations
                            + ClientDestinations
                )
            }
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // Marshmallow+ Permission APIs
            stuffMarshMallow();
        }
    }

    companion object {
        val Start = createSimpleDestination(NavigationConst.START)
        val Server = createSimpleDestination(NavigationConst.SERVER)
        val Client = createSimpleDestination(NavigationConst.CLIENT)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when (requestCode) {
            REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS -> {
                val perms: MutableMap<String, Int> = HashMap()
                // Initial
                perms[android.Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED


                // Fill with results
                var i = 0
                while (i < permissions.size) {
                    perms[permissions[i]] = grantResults[i]
                    i++
                }

                // Check for ACCESS_FINE_LOCATION
                if (perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED) {
                    // All Permissions Granted

                    // Permission Denied
                    Toast.makeText(
                        this,
                        "All Permission GRANTED !! Thank You :)",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    // Permission Denied
                    Toast.makeText(
                        this,
                        "One or More Permissions are DENIED Exiting App :(",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    finish()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun stuffMarshMallow() {
        val permissionsNeeded: MutableList<String> = ArrayList()
        val permissionsList: MutableList<String> = ArrayList()
        if (!addPermission(
                permissionsList,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) permissionsNeeded.add("Show Location")
        if (permissionsList.size > 0) {
            if (permissionsNeeded.size > 0) {

                // Need Rationale
                var message = "App need access to " + permissionsNeeded[0]
                for (i in 1 until permissionsNeeded.size) message =
                    message + ", " + permissionsNeeded[i]
                showMessageOKCancel(
                    message
                ) { dialog, which ->
                    requestPermissions(
                        permissionsList.toTypedArray(),
                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
                    )
                }
                return
            }
            requestPermissions(
                permissionsList.toTypedArray(),
                REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS
            )
            return
        }
        Toast.makeText(
            this,
            "No new Permission Required- Launching App .You are Awesome!!",
            Toast.LENGTH_SHORT
        )
            .show()
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun addPermission(permissionsList: MutableList<String>, permission: String): Boolean {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission)
            // Check for Rationale Option
            if (!shouldShowRequestPermissionRationale(permission)) return false
        }
        return true
    }
}


