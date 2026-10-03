package com.pipo.robot.lock

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.pipo.robot.R
import com.pipo.robot.voice.VoiceTrigger

/**
 * "Pipo lock": say it (or type it) and your phone locks, the same as pressing the power button,
 * so it opens again normally with your fingerprint, face or PIN. Made for the moment a friend
 * grabs your phone in class.
 *
 * Locking uses Android's own [DevicePolicyManager] — a device admin that declares exactly the
 * platform's `force-lock` policy, and nothing else. No accessibility service is involved: nothing
 * reads your screen, your apps or what you type, and Android shows its own activation screen
 * before anything works at all.
 *
 * The phrase itself is plain text matching ([VoiceTrigger] / [matches]) over a transcript the
 * phone's speech recogniser produced; this file never touches audio.
 */
object PipoLock {
    fun admin(ctx: Context) = ComponentName(ctx, PipoDeviceAdmin::class.java)

    /** Is the lock helper switched on in Android's device-admin settings? */
    fun canLock(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(ctx)) }.getOrDefault(false)

    /**
     * Android's own "activate device admin" screen. Nothing happens until you tap Activate there.
     * Launch it for a result from an Activity, never with FLAG_ACTIVITY_NEW_TASK: Android closes
     * that screen immediately if it's started as a new task.
     */
    fun enrollIntent(ctx: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.lock_helper_description))

    /** Switch the helper off again. You can re-enable it any time. */
    fun disengage(ctx: Context) {
        runCatching { ctx.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(admin(ctx)) }
    }

    /** Lock the phone now. False when the helper isn't switched on (he says so; Settings can fix it). */
    fun lockNow(ctx: Context): Boolean {
        if (!canLock(ctx)) return false
        return runCatching {
            ctx.getSystemService(DevicePolicyManager::class.java).lockNow()
            true
        }.getOrDefault(false)
    }

    /**
     * Did that sound like "Pipo lock"? Speech recognisers hear "Pipo" a few ways ("peepo", "pippo",
     * "pepo"...), and "lock" as "lok"/"log"/"locked". "Pipo, lock my phone" counts too.
     */
    private val phrase = Regex(
        "\\b${VoiceTrigger.PIPO}\\b[\\s,.!]*(please\\s+)?(lock|lok|locked|log|loch|lock it)\\b|" +
            "\\block (my|the) phone\\b.*\\b${VoiceTrigger.PIPO}\\b"
    )

    fun matches(text: String): Boolean = phrase.containsMatchIn(text.lowercase())
}

/**
 * Android's device admin for Pipo. It requests a single policy — `force-lock` (see
 * `res/xml/pipo_device_admin.xml`) — which is what makes [DevicePolicyManager.lockNow] legal.
 * It cannot wipe, reset passwords, disable anything, or read anything.
 */
class PipoDeviceAdmin : DeviceAdminReceiver()
