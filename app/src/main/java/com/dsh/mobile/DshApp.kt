package com.dsh.mobile

import android.app.Application
import com.dsh.mobile.data.BridgeRepository

class DshApp : Application() {

    val repo: BridgeRepository by lazy { BridgeRepository(this) }
}
