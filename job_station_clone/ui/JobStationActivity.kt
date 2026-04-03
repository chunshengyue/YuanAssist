package com.example.yuanassist.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.yuanassist.R

class JobStationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_FILE_NAME = "extra_asset_file_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_SUMMARY = "extra_summary"
        const val EXTRA_STAGE = "extra_stage"
        const val EXTRA_ROSTER = "extra_roster"
        const val EXTRA_SEQUENCE = "extra_sequence"
        const val EXTRA_ORIGINAL_AUTHOR = "extra_original_author"
        const val EXTRA_ORIGINAL_LINK = "extra_original_link"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_station)

        bindHeader()
        bindContent(loadJobStationData())
    }

    private fun bindHeader() {
        findViewById<ImageView>(R.id.btn_job_station_back).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tv_job_station_title).text =
            intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "白鹄作业站"

        findViewById<TextView>(R.id.tv_job_station_author).text =
            intent.getStringExtra(EXTRA_AUTHOR)?.takeIf { it.isNotBlank() } ?: "作者占位"
    }

    private fun bindContent(data: JobStationAssetRepository.JobStationDetailData) {
        findViewById<TextView>(R.id.tv_job_station_summary).text =
            intent.getStringExtra(EXTRA_SUMMARY)?.takeIf { it.isNotBlank() } ?: data.summary

        findViewById<TextView>(R.id.tv_job_station_stage).text =
            intent.getStringExtra(EXTRA_STAGE)?.takeIf { it.isNotBlank() } ?: data.stage

        findViewById<TextView>(R.id.tv_job_station_roster).text =
            intent.getStringExtra(EXTRA_ROSTER)?.takeIf { it.isNotBlank() } ?: data.roster

        findViewById<TextView>(R.id.tv_job_station_sequence).text =
            intent.getStringExtra(EXTRA_SEQUENCE)?.takeIf { it.isNotBlank() } ?: data.sequence

        findViewById<TextView>(R.id.tv_job_station_original_author).text =
            intent.getStringExtra(EXTRA_ORIGINAL_AUTHOR)?.takeIf { it.isNotBlank() } ?: "原作者占位"

        val originalLink = intent.getStringExtra(EXTRA_ORIGINAL_LINK)?.takeIf { it.isNotBlank() }
        val originalLinkView = findViewById<TextView>(R.id.tv_job_station_original_link)
        originalLinkView.text = originalLink ?: "原链接占位"
        originalLinkView.setOnClickListener {
            val url = intent.getStringExtra(EXTRA_ORIGINAL_LINK)?.takeIf { value -> value.isNotBlank() }
                ?: return@setOnClickListener
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun loadJobStationData(): JobStationAssetRepository.JobStationDetailData {
        val assetFileName = intent.getStringExtra(EXTRA_ASSET_FILE_NAME)
        val data = JobStationAssetRepository.loadDetail(this, assetFileName)
        findViewById<TextView>(R.id.tv_job_station_title).text =
            intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: data.title
        return data
    }
}
