package com.android.calculator2.vault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.calculator2.PasswordManager
import com.android.calculator2.R

class VaultActivity : AppCompatActivity(), MediaAdapter.OnItemClickListener {

    companion object {
        private const val REQUEST_PERMISSION_IMPORT = 1001
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var bottomActionBar: LinearLayout
    private lateinit var btnExport: View
    private lateinit var btnSelectAll: View
    private lateinit var btnDelete: View

    private lateinit var adapter: MediaAdapter
    private lateinit var repository: VaultRepository

    private var isSelectMode = false

    private val deleteOriginalLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        loadMedia()
    }

    // 导入文件选择器
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importFiles(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = VaultRepository.getInstance(this)
        repository.initVault()

        recyclerView = findViewById(R.id.recycler_media)
        emptyView = findViewById(R.id.empty_view)
        bottomActionBar = findViewById(R.id.bottom_action_bar)
        btnExport = findViewById(R.id.btn_export)
        btnSelectAll = findViewById(R.id.btn_select_all)
        btnDelete = findViewById(R.id.btn_delete)

        adapter = MediaAdapter(emptyList(), this)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        btnExport.setOnClickListener { exportSelected() }
        btnSelectAll.setOnClickListener { adapter.selectAll() }
        btnDelete.setOnClickListener { deleteSelected() }

        loadMedia()
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_vault, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_import -> {
                startImport()
                true
            }
            R.id.action_password -> {
                showPasswordSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onItemClick(position: Int, item: MediaItem) {
        if (isSelectMode) {
            item.selected = !item.selected
            adapter.notifyItemChanged(position)
            updateSelectModeUI()
        } else {
            // 打开查看器
            val intent = Intent(this, MediaViewerActivity::class.java)
            intent.putExtra(MediaViewerActivity.EXTRA_MEDIA_LIST, ArrayList(adapter.items.map { it.path }))
            intent.putExtra(MediaViewerActivity.EXTRA_MEDIA_TYPES, ArrayList(adapter.items.map { it.type.name }))
            intent.putExtra(MediaViewerActivity.EXTRA_POSITION, position)
            startActivity(intent)
        }
    }

    override fun onItemLongClick(position: Int, item: MediaItem): Boolean {
        if (!isSelectMode) {
            enterSelectMode()
            item.selected = true
            adapter.notifyItemChanged(position)
            updateSelectModeUI()
        }
        return true
    }

    override fun onBackPressed() {
        if (isSelectMode) {
            exitSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadMedia() {
        val items = repository.getAllMedia()
        adapter.updateItems(items)

        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun startImport() {
        if (!checkPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ),
                REQUEST_PERMISSION_IMPORT
            )
            return
        }
        openFilePicker()
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_IMPORT) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        try {
            importLauncher.launch(arrayOf("image/*", "video/*"))
        } catch (e: Exception) {
            // 回退方案：使用 ACTION_GET_CONTENT
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            fallbackImportLauncher.launch(intent)
        }
    }

    // 回退方案
    private val fallbackImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } ?: result.data?.data?.let { uri ->
                uris.add(uri)
            }
            if (uris.isNotEmpty()) {
                importFiles(uris)
            }
        }
    }

    private fun importFiles(uris: List<Uri>) {
        Thread {
            val result = repository.importFromGallery(uris)
            runOnUiThread {
                loadMedia()
                Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()

                if (result.originalNames.isNotEmpty()) {
                    requestDeleteOriginals(result.originalNames)
                }
            }
        }.start()
    }

    private fun requestDeleteOriginals(names: List<Pair<String, Boolean>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = repository.findMediaStoreUris(names)
            if (mediaStoreUris.isNotEmpty()) {
                try {
                    val deleteRequest = MediaStore.createDeleteRequest(contentResolver, mediaStoreUris)
                    deleteOriginalLauncher.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
                } catch (_: Exception) {}
            }
        }
    }

    private fun exportSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        Thread {
            repository.exportToGallery(selected)
            runOnUiThread {
                Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
                exitSelectMode()
            }
        }.start()
    }

    private fun deleteSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_confirm, selected.size))
            .setPositiveButton(R.string.confirm) { _, _ ->
                Thread {
                    repository.deleteItems(selected)
                    runOnUiThread {
                        exitSelectMode()
                        loadMedia()
                    }
                }.start()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun enterSelectMode() {
        isSelectMode = true
        adapter.isSelectMode = true
        adapter.notifyDataSetChanged()
        bottomActionBar.visibility = View.VISIBLE
    }

    private fun exitSelectMode() {
        isSelectMode = false
        adapter.isSelectMode = false
        adapter.deselectAll()
        bottomActionBar.visibility = View.GONE
    }

    private fun updateSelectModeUI() {
        val count = adapter.getSelectedItems().size
        supportActionBar?.title = if (count > 0) {
            getString(R.string.media_count, count)
        } else {
            getString(R.string.title_vault)
        }
    }

    private fun showPasswordSettings() {
        val pm = PasswordManager.getInstance(this)
        val currentPwd = pm.getPassword()

        val dialogView = layoutInflater.inflate(R.layout.dialog_password_settings, null)
        val textCurrent = dialogView.findViewById<android.widget.TextView>(R.id.text_current_password)
        val editNew = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_new_password)
        val editConfirm = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_confirm_password)

        textCurrent.text = "${getString(R.string.current_password)}: $currentPwd"

        AlertDialog.Builder(this)
            .setTitle(R.string.password_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newPwd = editNew.text.toString().trim()
                val confirmPwd = editConfirm.text.toString().trim()

                when {
                    newPwd.isEmpty() -> {
                        Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
                    }
                    newPwd != confirmPwd -> {
                        Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        pm.setPassword(newPwd)
                        Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}