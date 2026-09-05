package io.github.daisukikaffuchino.han1meviewer.ui.player

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext

actual fun isCastAvailable(): Boolean {
    return GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(Han1meDatabaseContext.appContext) == ConnectionResult.SUCCESS
}
