// File: MainActivity.kt
package com.example.travelupa

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.travelupa.ui.theme.TravelupaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.TopAppBar


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val currentUser: FirebaseUser? = FirebaseAuth.getInstance().currentUser
        setContent {
            TravelupaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(currentUser)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(currentUser: FirebaseUser?) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (currentUser != null)
            Screen.RekomendasiTempat.route else Screen.Greeting.route
    ) {
        composable(Screen.Greeting.route) {
            GreetingScreen(
                onStart = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Greeting.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.RekomendasiTempat.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.RekomendasiTempat.route) {
            RekomendasiTempatScreen(
                onBackToLogin = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Greeting.route) {
                        popUpTo(Screen.RekomendasiTempat.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun GreetingScreen(
    onStart: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // DIUBAH: .h4 menjadi .headlineLarge
            Text(
                text = "Selamat Datang di Travelupa!",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            // DIUBAH: .h6 menjadi .titleLarge
            Text(
                text = "Solusi buat kamu yang lupa kemana-mana",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier
                .width(360.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Mulai")
        }
    }
}
data class TempatWisata(
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null,
    @Transient val gambarResId: Int? = null // @Transient agar tidak ikut di-serialize oleh Firestore
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekomendasiTempatScreen(onBackToLogin: () -> Unit) {
    var daftarTempatWisata by remember { mutableStateOf(listOf<TempatWisata>()) }
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        firestore.collection("tempat_wisata").get()
            .addOnSuccessListener { result ->
                val list = result.map { document ->
                    document.toObject(TempatWisata::class.java)
                }
                daftarTempatWisata = list
            }
            .addOnFailureListener { exception ->
                Log.w("RekomendasiScreen", "Error getting documents.", exception)
                Toast.makeText(context, "Gagal memuat data.", Toast.LENGTH_SHORT).show()
            }
    }

    var showTambahDialog by remember { mutableStateOf(false) }

    Scaffold(
        // ================== BAGIAN YANG DITAMBAHKAN ==================
        topBar = {
            TopAppBar(
                title = { Text("Rekomendasi Wisata") },
                actions = {
                    // Tombol Logout ada di sini
                    IconButton(onClick = onBackToLogin) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        },
        // =============================================================
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTambahDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah Tempat Wisata")
            }
        }
    ) { paddingValues ->
        // Isi dari Column tetap sama
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(daftarTempatWisata) { tempat ->
                    TempatItemEditable(
                        tempat = tempat,
                        onDelete = {
                            firestore.collection("tempat_wisata").document(tempat.nama).delete()
                                .addOnSuccessListener {
                                    daftarTempatWisata = daftarTempatWisata.filter { it.nama != tempat.nama }
                                    Toast.makeText(context, "${tempat.nama} berhasil dihapus.", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Gagal menghapus.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    )
                }
            }
        }

        if (showTambahDialog) {
            TambahTempatWisataDialog(
                firestore = firestore,
                context = context,
                onDismiss = { showTambahDialog = false },
                onTambah = { tempatBaru ->
                    daftarTempatWisata = daftarTempatWisata + tempatBaru
                    showTambahDialog = false
                }
            )
        }
    }
}

@Composable
fun TempatItemEditable(
    tempat: TempatWisata,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        // FIX 5: Penggunaan elevation di Material 3
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Image(
                painter = tempat.gambarUriString?.let { uriString ->
                    rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(uriString) // FIX 6: Cukup kirim string URL
                            .crossfade(true)
                            .build()
                    )
                } ?: painterResource(id = R.drawable.default_image), // Pastikan ada default_image.png
                contentDescription = tempat.nama,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    // FIX 7 & 8: Menggunakan Text Style dari Material 3
                    Text(text = tempat.nama, style = MaterialTheme.typography.titleLarge)
                    Text(text = tempat.deskripsi, style = MaterialTheme.typography.bodyMedium)
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }) {
                        // FIX 9: Ikon MoreVert perlu di-import
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        offset = DpOffset((-16).dp, 0.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                expanded = false
                                onDelete() // Memanggil lambda onDelete
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun TambahTempatWisataDialog(
    firestore: FirebaseFirestore,
    context: Context,
    onDismiss: () -> Unit,
    onTambah: (TempatWisata) -> Unit
) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) } // Ubah nama variabel agar lebih jelas

    val gambarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        gambarUri = uri
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Tambah Tempat Wisata Baru") },
        text = {
            Column {
                TextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama Tempat") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = deskripsi, onValueChange = { deskripsi = it }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving)
                Spacer(modifier = Modifier.height(8.dp))
                gambarUri?.let {
                    Image(painter = rememberAsyncImagePainter(model = it), contentDescription = "Gambar yang dipilih", modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { gambarLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                    Text("Pilih Gambar")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Pastikan nama, deskripsi, dan gambar sudah diisi
                    if (nama.isNotBlank() && deskripsi.isNotBlank() && gambarUri != null) {
                        isSaving = true

                        // UBAH BAGIAN INI: Kita tidak upload, tapi langsung buat objeknya
                        val tempatBaru = TempatWisata(
                            nama = nama,
                            deskripsi = deskripsi,
                            // Simpan path lokal URI-nya sebagai string
                            gambarUriString = gambarUri.toString()
                        )

                        // Langsung simpan objek ke Firestore
                        firestore.collection("tempat_wisata").document(nama)
                            .set(tempatBaru)
                            .addOnSuccessListener {
                                // Jika sukses, panggil callback onTambah dan tutup dialog
                                isSaving = false
                                Toast.makeText(context, "Data berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                onTambah(tempatBaru)
                            }
                            .addOnFailureListener { exception ->
                                // Jika gagal, tampilkan pesan error
                                isSaving = false
                                Log.e("FirestoreError", "Gagal menyimpan data", exception)
                                Toast.makeText(context, "Gagal menyimpan: ${exception.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        Toast.makeText(context, "Harap isi semua data dan pilih gambar.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Tambah")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}

// FIX 12: Fungsi uploadImageToFirestore yang hilang, sekarang dibuat
private fun uploadImageToFirestore(
    firestore: FirebaseFirestore,
    context: Context,
    imageUri: Uri,
    nama: String,
    deskripsi: String,
    onSuccess: (TempatWisata) -> Unit,
    onFailure: (Exception) -> Unit
) {
    val storageRef = FirebaseStorage.getInstance().reference
    val imageFileName = "${UUID.randomUUID()}.jpg"
    val imageRef = storageRef.child("images/$imageFileName")

    // Langkah 1: Mulai upload file
    imageRef.putFile(imageUri)
        // Langkah 2: Setelah upload SELESAI, lanjutkan dengan tugas mengambil URL
        .continueWithTask { task ->
            if (!task.isSuccessful) {
                // Jika upload gagal, lemparkan error-nya
                task.exception?.let { throw it }
            }
            // Jika upload berhasil, kembalikan tugas untuk mengambil URL download
            imageRef.downloadUrl
        }
        // Langkah 3: Listener ini hanya akan berjalan setelah URL download berhasil didapatkan
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUri = task.result
                val tempatWisata = TempatWisata(
                    nama = nama,
                    deskripsi = deskripsi,
                    gambarUriString = downloadUri.toString()
                )
                // Simpan data ke Firestore
                firestore.collection("tempat_wisata").document(nama)
                    .set(tempatWisata)
                    .addOnSuccessListener {
                        onSuccess(tempatWisata) // Panggil callback sukses
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception) // Panggil callback gagal
                    }
            } else {
                // Jika gagal mendapatkan URL download, panggil callback gagal
                task.exception?.let { onFailure(it) }
            }
        }
}


@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Login", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it; errorMessage = null }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it; errorMessage = null }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter email and password"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).await()
                        }
                        onLoginSuccess()
                    } catch (e: Exception) {
                        errorMessage = "Login failed: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Login")
            }
        }
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}