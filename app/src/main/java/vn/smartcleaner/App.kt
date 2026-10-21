package vn.smartcleaner

import android.app.Application
import vn.smartcleaner.data.*

class App : Application() {
    val db by lazy { AppDb.get(this) }
    val prefs by lazy { Prefs(this) }
    val media by lazy { MediaRepository(this, db) }
    val trash by lazy { Trash(this, db) }
    val apps by lazy { Apps(this) }
    val junk by lazy { Junk(this) }
    val faces by lazy { Faces(this) }
}
