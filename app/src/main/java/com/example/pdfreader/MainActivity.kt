package com.example.pdfreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: RecentAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var rvRecents: RecyclerView

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Keep permission to reopen this file from Recents later.
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some providers don't grant persistable permission; the
                // document still opens now, it just may drop off Recents.
            }
            val name = Storage.displayName(this, uri)
            Storage.addRecent(this, uri, name)
            openReader(uri, name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEmpty = findViewById(R.id.tvEmpty)
        rvRecents = findViewById(R.id.rvRecents)
        adapter = RecentAdapter { doc -> openReader(Uri.parse(doc.uri), doc.name) }
        rvRecents.layoutManager = LinearLayoutManager(this)
        rvRecents.adapter = adapter

        findViewById<MaterialButton>(R.id.btnOpenPdf).setOnClickListener {
            openDocument.launch(arrayOf("application/pdf"))
        }

        // Tool tiles.
        val tiles = listOf(
            R.id.tileImagesToPdf to ImagesToPdfActivity::class.java,
            R.id.tileMerge to MergePdfActivity::class.java,
            R.id.tileSplit to SplitPdfActivity::class.java,
            R.id.tilePdfToImage to PdfToImageActivity::class.java,
            R.id.tileCompress to CompressImageActivity::class.java
        )
        tiles.forEachIndexed { index, (viewId, activityClass) ->
            val tile = findViewById<View>(viewId)
            tile.setOnClickListener {
                startActivity(Intent(this, activityClass))
                overridePendingTransition(
                    R.anim.activity_fade_in, R.anim.activity_fade_out
                )
            }
            // Staggered entrance animation for the tool tiles.
            val animation = AnimationUtils.loadAnimation(this, R.anim.fall_down)
            animation.startOffset = index * 70L
            tile.startAnimation(animation)
        }
    }

    override fun onResume() {
        super.onResume()
        val recents = Storage.getRecents(this)
        adapter.submit(recents)
        tvEmpty.visibility = if (recents.isEmpty()) View.VISIBLE else View.GONE
        rvRecents.scheduleLayoutAnimation()
    }

    private fun openReader(uri: Uri, name: String) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_URI, uri.toString())
            putExtra(ReaderActivity.EXTRA_NAME, name)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out)
    }
}

class RecentAdapter(
    private val onClick: (RecentDocument) -> Unit
) : RecyclerView.Adapter<RecentAdapter.RecentViewHolder>() {

    private var items: List<RecentDocument> = emptyList()

    fun submit(list: List<RecentDocument>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent, parent, false)
        return RecentViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val doc = items[position]
        holder.name.text = doc.name
        val whenText = if (doc.openedAt > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(doc.openedAt))
        } else ""
        holder.meta.text = whenText
        holder.itemView.setOnClickListener { onClick(doc) }
    }

    override fun getItemCount(): Int = items.size

    class RecentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val meta: TextView = view.findViewById(R.id.tvMeta)
    }
}
