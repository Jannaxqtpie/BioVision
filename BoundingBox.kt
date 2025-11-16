package com.surendramaran.yolov8tflite

import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val cnf: Float,
    val cls: Int,
    val clsName: String,
    var textColor: Int = Color.WHITE // Added textColor property with default value
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readInt() // Read textColor from Parcel
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeFloat(x1)
        parcel.writeFloat(y1)
        parcel.writeFloat(x2)
        parcel.writeFloat(y2)
        parcel.writeFloat(cx)
        parcel.writeFloat(cy)
        parcel.writeFloat(w)
        parcel.writeFloat(h)
        parcel.writeFloat(cnf)
        parcel.writeInt(cls)
        parcel.writeString(clsName)
        parcel.writeInt(textColor) // Write textColor to Parcel
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BoundingBox> {
        override fun createFromParcel(parcel: Parcel): BoundingBox {
            return BoundingBox(parcel)
        }

        override fun newArray(size: Int): Array<BoundingBox?> {
            return arrayOfNulls(size)
        }
    }
}
