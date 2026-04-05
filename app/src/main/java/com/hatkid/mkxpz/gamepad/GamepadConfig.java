package com.hatkid.mkxpz.gamepad;

import android.view.KeyEvent;

public class GamepadConfig
{
    /** In-screen gamepad settings **/

    // Opacity of view elements in percentage (default: 36)
    public Integer opacity = 36;

    // View elements scale in percentage (default: 100)
    public Integer scale = 100;

    // Whether use diagonal (8-way) movement on D-Pad (default: false)
    public Boolean diagonalMovement = false;

    /** Key bindings for each RGSS input **/
    public final Integer keycodeMenu = KeyEvent.KEYCODE_A;
    public final Integer keycodeCancel = KeyEvent.KEYCODE_X;
    public final Integer keycodeConfirm = KeyEvent.KEYCODE_Z;
    public final Integer keycodeSprint = KeyEvent.KEYCODE_SHIFT_LEFT;
}
