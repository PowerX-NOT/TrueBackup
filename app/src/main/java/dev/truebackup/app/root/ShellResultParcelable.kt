package dev.truebackup.app.root

import android.os.Parcel
import android.os.Parcelable

data class ShellResultParcelable(
    val code: Int,
    val output: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        code = parcel.readInt(),
        output = parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(code)
        parcel.writeString(output)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ShellResultParcelable> {
        override fun createFromParcel(parcel: Parcel): ShellResultParcelable = ShellResultParcelable(parcel)

        override fun newArray(size: Int): Array<ShellResultParcelable?> = arrayOfNulls(size)
    }
}
