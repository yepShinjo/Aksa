package com.mari.aksa.tutorial

import android.annotation.SuppressLint
import android.content.Context

private const val PREFS = "aksa_prefs"

// status pernah selesai (agar first-launch hanya sekali)
private const val KEY_GUIDE_DONE_ONCE = "guide_done_once"

// chain mode (kalau true, selesai satu layar → otomatis lanjut layar berikut)
private const val KEY_GUIDE_CHAIN = "guide_chain"

// pending per layar (akan “dikonsumsi” oleh masing-masing page)
private const val PENDING_HOME = "pending_home"
private const val PENDING_DICT = "pending_dict"
private const val PENDING_SETTING = "pending_setting"

fun isFirstLaunchGuideNeeded(ctx: Context): Boolean =
    !ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_GUIDE_DONE_ONCE, false)

@SuppressLint("UseKtx")
fun markAllGuidesDoneOnce(ctx: Context) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_GUIDE_DONE_ONCE, true).apply()
}

@SuppressLint("UseKtx")
private fun setPending(ctx: Context, key: String, value: Boolean) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(key, value).apply()
}

@SuppressLint("UseKtx")
private fun consume(ctx: Context, key: String): Boolean {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val v = p.getBoolean(key, false)
    if (v) p.edit().putBoolean(key, false).apply()
    return v
}

@SuppressLint("UseKtx")
fun enableChain(ctx: Context, on: Boolean) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_GUIDE_CHAIN, on).apply()
}
fun isChain(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_GUIDE_CHAIN, false)

fun startFullGuide(ctx: Context) {
    // set 3 layar sebagai pending dan nyalakan chain
    setPending(ctx, PENDING_HOME, true)
    setPending(ctx, PENDING_DICT, true)
    setPending(ctx, PENDING_SETTING, true)
    enableChain(ctx, true)
}

fun scheduleHomeOnly(ctx: Context) = setPending(ctx, PENDING_HOME, true)
fun scheduleDictOnly(ctx: Context) = setPending(ctx, PENDING_DICT, true)
fun scheduleSettingOnly(ctx: Context) = setPending(ctx, PENDING_SETTING, true)

fun consumeHome(ctx: Context) = consume(ctx, PENDING_HOME)
fun consumeDict(ctx: Context) = consume(ctx, PENDING_DICT)
fun consumeSetting(ctx: Context) = consume(ctx, PENDING_SETTING)
