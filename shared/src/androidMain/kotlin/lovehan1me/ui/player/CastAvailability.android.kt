package lovehan1me.ui.player

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import lovehan1me.data.database.dao.Han1meDatabaseContext

actual fun isCastAvailable(): Boolean {
    return GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(Han1meDatabaseContext.appContext) == ConnectionResult.SUCCESS
}
