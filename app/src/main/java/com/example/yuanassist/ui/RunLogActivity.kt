package com.example.yuanassist.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.R
import com.example.yuanassist.utils.RunLogger

class RunLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_log)

        val tvLog = findViewById<TextView>(R.id.tv_run_log_content)
        val logs = RunLogger.getAllLogs()

        if (logs.isNotEmpty()) {
            tvLog.text = logs
        }
    }
}