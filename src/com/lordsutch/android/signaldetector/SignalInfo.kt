package com.lordsutch.android.signaldetector

import android.os.Parcelable
import android.telephony.TelephonyManager
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
data class OtherLteCell(var gci: Int = Int.MAX_VALUE,
                        var pci: Int = Int.MAX_VALUE,
                        var tac: Int = Int.MAX_VALUE,
                        var mcc: Int = Int.MAX_VALUE,
                        var mnc: Int = Int.MAX_VALUE,
                        var mccString: String? = null,
                        var mncString: String? = null,
                        var earfcn: Int = Int.MAX_VALUE,
                        var lteBand: Int = 0,
                        var isFDD: Boolean = false,
                        var lteSigStrength: Int = Int.MAX_VALUE,
                        var timingAdvance: Int = Int.MAX_VALUE) : Parcelable {
    fun formatPLMN() : String {
        if(mccString != null && mncString != null)
            return String.format(Locale.US, "%s-%s", mccString, mncString)
        else
            return ""
    }
}

@Parcelize
data class SignalInfo(
        var longitude: Double = 0.0,
        var latitude: Double = 0.0,
        var altitude: Double = 0.0,
        var accuracy: Double = 0.0,
        var speed: Double = 0.0,
        var avgSpeed: Double = 0.0,
        var bearing: Double = 0.0,
        var fixAge: Long = 0, // in milliseconds

        // LTE
        var gci: Int = Int.MAX_VALUE,
        var pci: Int = Int.MAX_VALUE,
        var tac: Int = Int.MAX_VALUE,
        var mcc: Int = Int.MAX_VALUE,
        var mnc: Int = Int.MAX_VALUE,
        var mccString: String? = null,
        var mncString: String? = null,
        var earfcn: Int = Int.MAX_VALUE,
        var lteSigStrength: Int = Int.MAX_VALUE,
        var timingAdvance: Int = Int.MAX_VALUE,

        var gsmTimingAdvance: Int = Int.MAX_VALUE,

        var lteBand: Int = 0,
        var isFDD: Boolean = false,

        // CDMA2000
        var bsid: Int = Int.MAX_VALUE,
        var nid: Int = Int.MAX_VALUE,
        var sid: Int = Int.MAX_VALUE,
        var bslat: Double = Double.NaN,
        var bslon: Double = Double.NaN,
        var cdmaSigStrength: Int = Int.MAX_VALUE,
        var evdoSigStrength: Int = Int.MAX_VALUE,

        // GSM/UMTS/W-CDMA
        var operator: String = "",
        var operatorName: String = "",
        var lac: Int = Int.MAX_VALUE,
        var cid: Int = Int.MAX_VALUE,
        var psc: Int = Int.MAX_VALUE,
        var rnc: Int = Int.MAX_VALUE,
        var fullCid: Int = Int.MAX_VALUE,
        var gsmSigStrength: Int = Int.MAX_VALUE,
        var bsic: Int = Int.MAX_VALUE,
        var uarfcn: Int = Int.MAX_VALUE,
        var arfcn: Int = Int.MAX_VALUE,

        var gsmMcc: Int = Int.MAX_VALUE,
        var gsmMnc: Int = Int.MAX_VALUE,
        var gsmMccString: String? = null,
        var gsmMncString: String? = null,

        var phoneType: Int = TelephonyManager.PHONE_TYPE_NONE,
        var networkType: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN,

        var roaming: Boolean = false,

        var otherCells: MutableList<OtherLteCell>? = null) : Parcelable {

    fun formatPLMN() : String {
        if(mccString != null && mncString != null)
            return String.format(Locale.US, "%s-%s", mccString, mncString)
        else
            return ""
    }

    fun formatGsmPLMN() : String {
        if(gsmMccString != null && gsmMncString != null)
            return String.format(Locale.US, "%s-%s", gsmMccString, gsmMncString)
        else
            return ""
    }
}