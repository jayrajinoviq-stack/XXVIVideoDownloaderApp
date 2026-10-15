package com.inoviq.videodownloader.videosaver.storysaver.ui.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.inoviq.videodownloader.videosaver.storysaver.adapter.BookmarkAdapter
import com.inoviq.videodownloader.videosaver.storysaver.databinding.ActivityBookmarkBinding
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkEntry
import com.inoviq.videodownloader.videosaver.storysaver.utils.BookmarkManager

class BookmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarkBinding
    private lateinit var bookmarkAdapter: BookmarkAdapter
    private var bookmarkList = mutableListOf<BookmarkEntry>()

    companion object {
        const val EXTRA_NAVIGATE_URL = "EXTRA_NAVIGATE_URL"
        const val EXTRA_NEW_TAB_URL = "EXTRA_NEW_TAB_URL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        binding = ActivityBookmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.llBookmarkRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.bookmarkTopBar.setPadding(
                (16 * density).toInt(),
                systemBars.top + (8 * density).toInt(),
                (16 * density).toInt(),
                (10 * density).toInt()
            )
            binding.rvBookmarkList.setPadding(
                0,
                0,
                0,
                systemBars.bottom + (20 * density).toInt()
            )
            insets
        }

        binding.btnBookmarkBack.setOnClickListener {
            finish()
        }

        setupBookmarkList()
    }

    private fun setupBookmarkList() {
        bookmarkList = BookmarkManager.getBookmarks(this)

        bookmarkAdapter = BookmarkAdapter(
            bookmarkList,
            onItemClick = { item ->
                val intent = Intent().apply {
                    putExtra(EXTRA_NAVIGATE_URL, item.url)
                }
                setResult(RESULT_OK, intent)
                finish()
            },
            onRibbonClick = { item, position ->
                BookmarkManager.removeBookmark(this, item)
                bookmarkAdapter.removeItemAt(position)
                updateEmptyState()
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
            },
            onAddTabClick = { item ->
                val intent = Intent().apply {
                    putExtra(EXTRA_NEW_TAB_URL, item.url)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        )

        binding.rvBookmarkList.layoutManager = LinearLayoutManager(this)
        binding.rvBookmarkList.adapter = bookmarkAdapter

        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (bookmarkList.isEmpty()) {
            binding.tvBookmarkEmpty.visibility = View.VISIBLE
            binding.rvBookmarkList.visibility = View.GONE
        } else {
            binding.tvBookmarkEmpty.visibility = View.GONE
            binding.rvBookmarkList.visibility = View.VISIBLE
        }
    }
}
