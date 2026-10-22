package me.luzility.muuusic

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class DiscordCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        container.discord.onRedirect(uri?.getQueryParameter("code"), uri?.getQueryParameter("state"), uri?.getQueryParameter("error"))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
