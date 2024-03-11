package KlepetChat.Activities

import KlepetChat.DataSore.Models.UserData
import KlepetChat.WebApi.Implementations.ApiResponse
import KlepetChat.WebApi.Implementations.ViewModels.AuthViewModel
import KlepetChat.WebApi.Implementations.ViewModels.ImageViewModel
import KlepetChat.WebApi.Implementations.ViewModels.UserDataViewModel
import KlepetChat.WebApi.Implementations.ViewModels.UserViewModel
import KlepetChat.WebApi.Models.Exceptions.ICoroutinesErrorHandler
import KlepetChat.WebApi.Models.Request.FIO
import KlepetChat.WebApi.Models.Request.Login
import KlepetChat.WebApi.Models.Response.Token
import KlepetChat.WebApi.Models.Response.User
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.klepetchat.R
import com.example.klepetchat.databinding.ActivityProfileBinding
import com.example.klepetchat.databinding.AlertDialogEditFioBinding
import com.example.klepetchat.databinding.AlertDialogEditNicknameBinding
import com.example.klepetchat.databinding.AlertDialogEditPhoneBinding
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@AndroidEntryPoint
class ProfileActivity : ComponentActivity() {
    private var binding: ActivityProfileBinding? = null
    private var bindingEditNickname: AlertDialogEditNicknameBinding? = null
    private var bindingEditPhone: AlertDialogEditPhoneBinding? = null
    private var bindingEditFIO: AlertDialogEditFioBinding? = null
    private val userDataViewModel: UserDataViewModel by viewModels()
    private val userViewModel: UserViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val imageViewModel: ImageViewModel by viewModels()
    private lateinit var user: User
    private lateinit var password: String
    private lateinit var file: File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        setListeners()
        setObserve()
        getUser()

    }

    private fun setObserve() {
        userViewModel.user.observe(this) { getUserApi(it) }
        userViewModel.userEditPhone.observe(this) { getUserEditPhoneApi(it) }
        authViewModel.token.observe(this) { getAccessToken(it) }
        imageViewModel.img.observe(this) { getHttpImage(it) }
    }

    private fun getHttpImage(api: ApiResponse<ResponseBody>) {
        when (api) {
            is ApiResponse.Success -> {
                var imageHttp = api.data.string()
                user.photo = imageHttp
                initProfile(user)
                if (file.exists()) {
                    file.delete()
                }
                putPhoto(imageHttp)
            }

            is ApiResponse.Failure -> {
                Toast.makeText(
                    this@ProfileActivity, "Ошибка! ${api.message}", Toast.LENGTH_SHORT
                ).show()
            }

            is ApiResponse.Loading -> {
                return
            }
        }
    }


    private fun getAccessToken(api: ApiResponse<Token>) {
        when (api) {
            is ApiResponse.Success -> {
                userDataViewModel.SaveUserData(
                    UserData(
                        user.phone,
                        api.data.accessToken.toString(),
                        api.data.refreshToken.toString()
                    )
                )
            }

            is ApiResponse.Failure -> {
                Toast.makeText(
                    this@ProfileActivity, "Ошибка! $api.message", Toast.LENGTH_SHORT
                ).show()
            }

            is ApiResponse.Loading -> {
                return
            }
        }
    }

    private fun getUserEditPhoneApi(api: ApiResponse<User>) {
        when (api) {
            is ApiResponse.Success -> {
                user = api.data
                initProfile(user)
                loginEditPone(user.phone, password)
                Toast.makeText(
                    this@ProfileActivity, "Номер успешно сменен!", Toast.LENGTH_SHORT
                ).show()
            }

            is ApiResponse.Failure -> {
                Toast.makeText(
                    this@ProfileActivity, "Ошибка! $api.message", Toast.LENGTH_SHORT
                ).show()
            }

            is ApiResponse.Loading -> {
                return
            }
        }
    }

    private fun getUserApi(api: ApiResponse<User>) {
        when (api) {
            is ApiResponse.Success -> {
                user = api.data
                initProfile(user)
            }

            is ApiResponse.Failure -> {
                Toast.makeText(
                    this@ProfileActivity, "Ошибка! $api.message", Toast.LENGTH_SHORT
                ).show()
                exitAuth()
            }

            is ApiResponse.Loading -> {
                return
            }
        }
    }

    private fun getUser() {
        userDataViewModel.userData.observe(this) {
            if (it?.accessToken.isNullOrBlank() && it?.phone.isNullOrBlank()) {
                exitAuth()
                return@observe
            }
            onUserSend(it!!.phone)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeListeners()
        binding = null
        bindingEditNickname = null
        bindingEditPhone = null
        bindingEditFIO = null
    }

    private fun removeListeners() {
        binding?.imageButtonBack?.setOnClickListener(null)
        binding?.editName?.setOnClickListener(null)
        binding?.editPhone?.setOnClickListener(null)
        binding?.editNickname?.setOnClickListener(null)
        binding?.inputMessageAboutMe?.onFocusChangeListener = null
        binding?.form?.setOnClickListener(null)
    }

    private fun setListeners() {
        binding?.imageButtonBack?.setOnClickListener { onBackPress() }
        binding?.imageUser?.setOnClickListener { onUserPress() }
        binding?.editName?.setOnClickListener { onEditName() }
        binding?.editPhone?.setOnClickListener { onEditPhone() }
        binding?.editNickname?.setOnClickListener { onEditNickname() }
        binding?.inputMessageAboutMe?.onFocusChangeListener = onChangeFocusAboutMe()
        binding?.form?.setOnClickListener { onClickForm() }
    }


    private fun onClickForm() {
        binding?.inputMessageAboutMe?.clearFocus()
    }

    private fun onChangeFocusAboutMe(): OnFocusChangeListener {
        return OnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                putAboutMe(binding?.inputMessageAboutMe?.text.toString() ?: "")
                if (getSystemService(Context.INPUT_METHOD_SERVICE) is InputMethodManager) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
    }

    private fun putPhoto(imageHttp: String) {
        userViewModel.putPhoto(imageHttp,
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun loginEditPone(phone: String, password: String) {
        authViewModel.login(
            Login(phone, password),
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun putAboutMe(aboutMe: String) {
        userViewModel.putAboutMe(aboutMe,
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun onEditNickname() {
        var dialog: AlertDialog.Builder = AlertDialog.Builder(this)
        var view =
            LayoutInflater.from(dialog.context).inflate(R.layout.alert_dialog_edit_nickname, null)
        bindingEditNickname = AlertDialogEditNicknameBinding.bind(view)
        bindingEditNickname?.nicknameField?.setText(binding?.textNickname?.text.toString())
        dialog.setView(view)
        dialog.setNegativeButton("Отменить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                dialog?.dismiss()
            })
        dialog.setPositiveButton("Сохранить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                if (bindingEditNickname?.nicknameField?.text.isNullOrBlank()) {
                    Toast.makeText(
                        this@ProfileActivity, "Имя пользователя не дожно быть пустым!!!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }
                putNickname(bindingEditNickname?.nicknameField?.text.toString())
            })
        dialog.show()
    }

    private fun onEditPhone() {
        var dialog: AlertDialog.Builder = AlertDialog.Builder(this)
        var view =
            LayoutInflater.from(dialog.context).inflate(R.layout.alert_dialog_edit_phone, null)
        bindingEditPhone = AlertDialogEditPhoneBinding.bind(view)
        dialog.setView(view)
        dialog.setNegativeButton("Отменить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                dialog?.dismiss()
            })
        dialog.setPositiveButton("Сохранить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                var password = bindingEditPhone!!.passwordField
                var phone = bindingEditPhone!!.phoneField;
                if (password.length() < 8) {
                    Toast.makeText(
                        applicationContext, "Слишком маленький пароль (не меньше 8)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }
                if (phone.text!!.length < 11) {
                    Toast.makeText(
                        applicationContext, "Такого номера телефона не существует!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }
                onEditPhone(
                    phone.text.toString(),
                    password.text.toString()
                )
            })
        dialog.show()
    }

    private fun onEditPhone(phone: String, password: String) {
        this.password = password
        userViewModel.putPhone(
            Login(phone, password),
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun onEditName() {
        var dialog: AlertDialog.Builder = AlertDialog.Builder(this)
        var view =
            LayoutInflater.from(dialog.context).inflate(R.layout.alert_dialog_edit_fio, null)
        bindingEditFIO = AlertDialogEditFioBinding.bind(view)
        var fio = binding?.textFIO?.text.toString().split(" ")
        bindingEditFIO?.surnameField?.setText(fio[0])
        bindingEditFIO?.nameField?.setText(fio[1])
        dialog.setView(view)
        dialog.setNegativeButton("Отменить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                dialog?.dismiss()
            })
        dialog.setPositiveButton("Сохранить",
            DialogInterface.OnClickListener { dialog: DialogInterface?, _ ->
                if (bindingEditFIO?.nameField?.text.isNullOrBlank() ||
                    bindingEditFIO?.surnameField?.text.isNullOrBlank()
                ) {
                    Toast.makeText(
                        this@ProfileActivity, "Фамилия и имя, не должны быть пустыми!!!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }
                putFIO(
                    bindingEditFIO?.surnameField?.text.toString(),
                    bindingEditFIO?.nameField?.text.toString()
                )
            })
        dialog.show()
    }

    private fun onUserSend(phone: String) {
        userViewModel.getByPhone(phone,
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun putNickname(nickname: String) {
        userViewModel.putNickname(nickname,
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun putFIO(surname: String, name: String) {
        userViewModel.putFIO(FIO(surname, name),
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun onBackPress() {
        var intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun onUserPress() {
        var photoPickerIntent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        photoPickerIntent.setType("image/*")
        getAction.launch(photoPickerIntent)
    }

    private val getAction =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            var bitmap: Bitmap? = null
            if (it.resultCode == RESULT_OK) {
                val selectedImage = it?.data?.data
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImage)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                val tempUri: Uri = getImageUri(applicationContext, bitmap!!)
                file = File(getRealPathFromURI(tempUri))
                val requestFile =
                    RequestBody.create("multipart/form-data".toMediaTypeOrNull(), file)
                val filePart =
                    MultipartBody.Part.createFormData("file", file.name, requestFile)
                postImg(filePart)
            }
        }

    private fun postImg(file: MultipartBody.Part) {
        imageViewModel.postImg(file,
            object : ICoroutinesErrorHandler {
                override fun onError(message: String) {
                    Toast.makeText(
                        this@ProfileActivity, "Ошибка $message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    fun getImageUri(inContext: Context, inImage: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(inContext.contentResolver, inImage, "Title", null)
        return Uri.parse(path)
    }

    fun getRealPathFromURI(uri: Uri?): String {
        val cursor = contentResolver.query(uri!!, null, null, null, null)
        var largeImagePath = ""
        try {
            cursor!!.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            largeImagePath = cursor.getString(idx)
        } finally {
            cursor?.close()
        }
        return largeImagePath
    }

    private fun exitAuth() {
        userDataViewModel.ClearUserData()
        var intent = Intent(this, AuthorizationActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initProfile(user: User) {
        var fio = "${user.surname} ${user.name}"
        binding?.textFIO?.text = fio
        binding?.textFIOEdit?.text = fio
        binding?.textPhone?.text = user.phone
        binding?.textNickname?.text = user.nickName
        binding?.inputMessageAboutMe?.setText(user.aboutMe)
        if (user.photo.isNullOrBlank()) {
            return
        }
        Picasso.get()
            .load(user.photo)
            .placeholder(R.drawable.baseline_account_circle_24)
            .error(R.drawable.baseline_account_circle_24)
            .into(binding?.imageUser)
    }
}