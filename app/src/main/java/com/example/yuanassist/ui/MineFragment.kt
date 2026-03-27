package com.example.yuanassist.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import cn.bmob.v3.BmobUser
import cn.bmob.v3.exception.BmobException
import cn.bmob.v3.listener.SaveListener
import cn.bmob.v3.listener.UpdateListener
import com.bumptech.glide.Glide
import com.example.yuanassist.R
import com.example.yuanassist.model.MyUser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MineFragment : Fragment() {
    private lateinit var tvSyncData: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvNickname: TextView
    private lateinit var tvUsername: TextView
    private lateinit var btnEdit: ImageView
    private lateinit var btnLogin: Button

    private var avatarDialog: AlertDialog? = null
    private var dialogAvatarPreview: ImageView? = null
    private var tempAvatarUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                tempAvatarUri = uri
                dialogAvatarPreview?.let {
                    Glide.with(this).load(uri).circleCrop().into(it)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_mine, container, false)
        initViews(view)
        loadUserData()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            loadUserData()
        }
    }

    private fun initViews(view: View) {
        ivAvatar = view.findViewById(R.id.iv_mine_avatar)
        tvNickname = view.findViewById(R.id.tv_mine_nickname)
        tvUsername = view.findViewById(R.id.tv_mine_username)
        btnEdit = view.findViewById(R.id.btn_edit_profile)
        btnLogin = view.findViewById(R.id.btn_one_click_login)
        tvSyncData = view.findViewById(R.id.tv_sync_data)

        btnLogin.setOnClickListener { performOneClickLogin() }
        view.findViewById<View>(R.id.layout_my_published_entry).setOnClickListener {
            startActivity(Intent(requireContext(), MyPublishedActivity::class.java))
        }
        view.findViewById<View>(R.id.layout_my_stone_entry).setOnClickListener {
            startActivity(Intent(requireContext(), MyStoneActivity::class.java))
        }
        view.findViewById<View>(R.id.layout_my_favorite_entry).setOnClickListener {
            startActivity(Intent(requireContext(), MyFavoriteActivity::class.java))
        }

        btnEdit.setOnClickListener {
            val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
            if (currentUser != null) {
                showEditNicknameDialog(currentUser)
            } else {
                Toast.makeText(requireContext(), "请先创建或登录账号", Toast.LENGTH_SHORT).show()
            }
        }

        tvSyncData.setOnClickListener {
            syncDataFromServer()
        }

        ivAvatar.setOnClickListener {
            val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
            if (currentUser != null) {
                showEditAvatarDialog(currentUser)
            }
        }
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)

        if (currentUser != null) {
            val cachedNickname = prefs.getString("nickname", currentUser.nickname) ?: "热心玩家"
            val cachedAvatar = prefs.getString("avatarUrl", currentUser.avatarUrl)

            tvNickname.text = cachedNickname
            tvUsername.text = "设备ID: ${currentUser.username}"
            btnLogin.visibility = View.GONE
            btnEdit.visibility = View.VISIBLE
            tvSyncData.visibility = View.VISIBLE

            if (!cachedAvatar.isNullOrEmpty()) {
                Glide.with(this).load(cachedAvatar).circleCrop()
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(ivAvatar)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_launcher_background)
            }
        } else {
            tvNickname.text = "未登录"
            tvUsername.text = "点击下方按钮绑定当前设备"
            tvSyncData.visibility = View.GONE
            ivAvatar.setImageResource(R.drawable.ic_launcher_background)
            btnLogin.visibility = View.VISIBLE
            btnEdit.visibility = View.GONE
        }
    }

    private fun showEditNicknameDialog(currentUser: MyUser) {
        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val currentName = prefs.getString("nickname", currentUser.nickname) ?: ""

        val editText = EditText(requireContext()).apply {
            setText(currentName)
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setView(editText)
            .setPositiveButton("确认") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    updateUserInBmob(newName, null)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditAvatarDialog(currentUser: MyUser) {
        tempAvatarUri = null
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_avatar_preview, null)

        dialogAvatarPreview = dialogView.findViewById(R.id.iv_dialog_avatar_preview)
        val btnSelect = dialogView.findViewById<Button>(R.id.btn_select_new_avatar)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_dialog_cancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_dialog_confirm)

        val prefs = requireContext().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        val currentAvatar = prefs.getString("avatarUrl", currentUser.avatarUrl)
        Glide.with(this)
            .load(currentAvatar)
            .circleCrop()
            .placeholder(R.drawable.ic_launcher_background)
            .into(dialogAvatarPreview!!)

        avatarDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        avatarDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSelect.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnCancel.setOnClickListener { avatarDialog?.dismiss() }
        btnConfirm.setOnClickListener {
            val selectedUri = tempAvatarUri
            if (selectedUri == null) {
                avatarDialog?.dismiss()
                return@setOnClickListener
            }

            btnConfirm.text = "上传中..."
            btnConfirm.isEnabled = false
            btnCancel.isEnabled = false
            btnSelect.isEnabled = false

            val cacheFile = uriToCacheFile(selectedUri)
            if (cacheFile == null) {
                Toast.makeText(requireContext(), "图片处理失败", Toast.LENGTH_SHORT).show()
                btnConfirm.text = "确认上传"
                btnConfirm.isEnabled = true
                btnCancel.isEnabled = true
                btnSelect.isEnabled = true
                return@setOnClickListener
            }

            uploadImageToImageBed(
                file = cacheFile,
                onSuccess = { url ->
                    activity?.runOnUiThread {
                        updateUserInBmob(null, url)
                        cacheFile.delete()
                    }
                },
                onError = { error ->
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "图片上传失败: $error", Toast.LENGTH_SHORT)
                            .show()
                        btnConfirm.text = "确认上传"
                        btnConfirm.isEnabled = true
                        btnCancel.isEnabled = true
                        btnSelect.isEnabled = true
                    }
                }
            )
        }

        avatarDialog?.show()
    }

    private fun updateUserInBmob(newNickname: String?, newAvatarUrl: String?) {
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        if (currentUser == null) {
            Toast.makeText(context ?: return, "状态异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        if (newNickname != null) currentUser.nickname = newNickname
        if (newAvatarUrl != null) currentUser.avatarUrl = newAvatarUrl

        currentUser.update(object : UpdateListener() {
            override fun done(e: BmobException?) {
                val ctx = context ?: return
                activity?.runOnUiThread {
                    if (e == null) {
                        Toast.makeText(ctx, "更新成功", Toast.LENGTH_SHORT).show()

                        val prefs = ctx.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            if (newNickname != null) putString("nickname", newNickname)
                            if (newAvatarUrl != null) putString("avatarUrl", newAvatarUrl)
                            apply()
                        }

                        loadUserData()
                        avatarDialog?.dismiss()
                    } else {
                        avatarDialog?.findViewById<Button>(R.id.btn_dialog_confirm)?.run {
                            text = "确认上传"
                            isEnabled = true
                        }
                    }
                }
            }
        })
    }

    private fun uriToCacheFile(uri: Uri): File? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val file = File(requireContext().cacheDir, "avatar_${System.currentTimeMillis()}.png")
            file.outputStream().use { inputStream.copyTo(it) }
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun uploadImageToImageBed(
        file: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val uploadUrl = "https://img.scdn.io/api/v1.php"
        val client = okhttp3.OkHttpClient()
        val mediaType = "image/*".toMediaTypeOrNull()
        val fileBody = file.asRequestBody(mediaType)

        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("image", file.name, fileBody)
            .addFormDataPart("outputFormat", "webp")
            .build()

        val request = okhttp3.Request.Builder()
            .url(uploadUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                onError("网络请求失败")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    try {
                        val json = org.json.JSONObject(responseBody)
                        if (json.optBoolean("success")) {
                            onSuccess(json.optString("url"))
                        } else {
                            onError(json.optString("message", "上传被拒"))
                        }
                    } catch (_: Exception) {
                        onError("JSON解析失败")
                    }
                } else {
                    onError("HTTP ${response.code}")
                }
            }
        })
    }

    private fun performOneClickLogin() {
        btnLogin.isEnabled = false
        btnLogin.text = "正在验证设备..."

        val deviceId = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

        val user = MyUser().apply {
            username = deviceId
            setPassword("123456")
            nickname = "玩家_$deviceId"
        }

        user.signUp(object : SaveListener<MyUser>() {
            override fun done(u: MyUser?, e: BmobException?) {
                val ctx = context ?: return
                activity?.runOnUiThread {
                    if (e == null && u != null) {
                        Toast.makeText(ctx, "账号创建成功", Toast.LENGTH_SHORT).show()
                        saveToLocalCache(u)
                        loadUserData()
                    } else if (e?.errorCode == 202) {
                        user.login(object : SaveListener<MyUser>() {
                            override fun done(lu: MyUser?, le: BmobException?) {
                                val loginCtx = context ?: return
                                activity?.runOnUiThread {
                                    if (le == null && lu != null) {
                                        saveToLocalCache(lu)
                                        loadUserData()
                                    } else {
                                        Toast.makeText(
                                            loginCtx,
                                            "登录失败: ${le?.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        btnLogin.isEnabled = true
                                        btnLogin.text = "一键创建/登录该设备账号"
                                    }
                                }
                            }
                        })
                    } else {
                        Toast.makeText(ctx, "创建失败: ${e?.message}", Toast.LENGTH_SHORT).show()
                        btnLogin.isEnabled = true
                        btnLogin.text = "一键创建/登录该设备账号"
                    }
                }
            }
        })
    }

    private fun saveToLocalCache(user: MyUser) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("nickname", user.nickname)
            putString("avatarUrl", user.avatarUrl)
            apply()
        }
    }

    private fun syncDataFromServer() {
        val currentUser = BmobUser.getCurrentUser(MyUser::class.java)
        if (currentUser == null) {
            Toast.makeText(context ?: return, "未登录，无法同步", Toast.LENGTH_SHORT).show()
            return
        }

        tvSyncData.text = "正在同步..."
        tvSyncData.isEnabled = false

        val query = cn.bmob.v3.BmobQuery<MyUser>()
        query.getObject(currentUser.objectId, object : cn.bmob.v3.listener.QueryListener<MyUser>() {
            override fun done(user: MyUser?, e: BmobException?) {
                val ctx = context ?: return
                activity?.runOnUiThread {
                    tvSyncData.text = "重新获取昵称和头像"
                    tvSyncData.isEnabled = true

                    if (e == null && user != null) {
                        Toast.makeText(ctx, "同步成功", Toast.LENGTH_SHORT).show()
                        val prefs = ctx.getSharedPreferences("user_cache", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("nickname", user.nickname)
                            putString("avatarUrl", user.avatarUrl)
                            apply()
                        }
                        loadUserData()
                    } else {
                        Toast.makeText(ctx, "同步失败: ${e?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
