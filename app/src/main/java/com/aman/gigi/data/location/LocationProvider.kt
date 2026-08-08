package com.aman.gigi.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            android.util.Log.e("LocationProvider", "❌ [CRITICAL] Location is DISABLED in system settings!")
            return null
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            
            // 1. Try to get HIGH ACCURACY fresh location with a 10s timeout
            val location: android.location.Location? = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).await()
                } catch (e: Exception) {
                    android.util.Log.e("LocationProvider", "GMS High Accuracy getCurrentLocation failed", e)
                    null
                }
            }

            if (location != null) {
                android.util.Log.i("LocationProvider", "📍 [SUCCESS] Got fresh high-accuracy location")
                return Pair(location.latitude, location.longitude)
            }

            // 2. Try to get BALANCED ACCURACY if high-accuracy fails or times out
            val balancedLocation: android.location.Location? = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellationTokenSource.token
                    ).await()
                } catch (e: Exception) {
                    android.util.Log.e("LocationProvider", "GMS Balanced Accuracy getCurrentLocation failed", e)
                    null
                }
            }

            if (balancedLocation != null) {
                android.util.Log.i("LocationProvider", "📍 [SUCCESS] Got balanced-accuracy location")
                return Pair(balancedLocation.latitude, balancedLocation.longitude)
            }

            android.util.Log.w("LocationProvider", "⚠️ [FALLBACK] Fresh location null/timeout, trying last known GMS...")
            
            // 3. Fallback: Try Last Known GMS Location
            val lastLocation = try {
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                android.util.Log.e("LocationProvider", "GMS lastLocation failed", e)
                null
            }
            if (lastLocation != null) {
                android.util.Log.i("LocationProvider", "📍 [SUCCESS] Using last known GMS location")
                return Pair(lastLocation.latitude, lastLocation.longitude)
            }

            android.util.Log.w("LocationProvider", "❌ [FAILURE] GMS Location unavailable. Triggering native LocationManager fallback...")
            return getNativeLocationFallback(locationManager, isGpsEnabled, isNetworkEnabled)
        } catch (e: Exception) {
            android.util.Log.e("LocationProvider", "Error in GMS flow. Triggering native LocationManager fallback...", e)
            return getNativeLocationFallback(locationManager, isGpsEnabled, isNetworkEnabled)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getNativeLocationFallback(
        locationManager: android.location.LocationManager,
        isGpsEnabled: Boolean,
        isNetworkEnabled: Boolean
    ): Pair<Double, Double>? {
        return try {
            val gpsLoc = if (isGpsEnabled) {
                locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            } else null
            
            val netLoc = if (isNetworkEnabled) {
                locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } else null
            
            val passiveLoc = locationManager.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
            
            val fallbackLoc = listOfNotNull(gpsLoc, netLoc, passiveLoc)
                .maxByOrNull { it.time }
                
            if (fallbackLoc != null) {
                android.util.Log.i("LocationProvider", "📍 [SUCCESS] Native LocationManager fallback succeeded")
                Pair(fallbackLoc.latitude, fallbackLoc.longitude)
            } else {
                android.util.Log.w("LocationProvider", "❌ [FAILURE] Native LocationManager fallback returned no location")
                null
            }
        } catch (ex: Exception) {
            android.util.Log.e("LocationProvider", "Exception during native fallback", ex)
            null
        }
    }
}
