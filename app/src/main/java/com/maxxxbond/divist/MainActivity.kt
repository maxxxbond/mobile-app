package com.maxxxbond.divist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.maxxxbond.divist.data.repository.FirestoreRepository
import com.maxxxbond.divist.ui.screen.ShoppingListsScreen
import com.maxxxbond.divist.ui.screen.ShoppingItemsScreen
import com.maxxxbond.divist.data.model.ShoppingList
import com.maxxxbond.divist.ui.theme.DivistTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var firestoreRepository: FirestoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Initialize Credential Manager
        credentialManager = CredentialManager.create(this)
        
        // Initialize Firestore Repository
        firestoreRepository = FirestoreRepository()
        
        // Handle deep link
        handleIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            DivistTheme {
                var currentUser by remember { mutableStateOf(auth.currentUser) }
                var selectedShoppingList by remember { mutableStateOf<ShoppingList?>(null) }
                var inviteInfo by remember { mutableStateOf<Pair<String, String>?>(null) } // listId, token
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                
                // Listen to auth state changes
                LaunchedEffect(Unit) {
                    auth.addAuthStateListener { firebaseAuth ->
                        currentUser = firebaseAuth.currentUser
                        if (firebaseAuth.currentUser == null) {
                            selectedShoppingList = null // Reset selected list on logout
                            inviteInfo = null
                        }
                    }
                }
                
                // Process invite if user is logged in
                LaunchedEffect(currentUser, inviteInfo) {
                    if (currentUser != null && inviteInfo != null) {
                        val (listId, token) = inviteInfo!!
                        try {
                            firestoreRepository.joinShoppingListByToken(listId, token)
                            Toast.makeText(context, "Успішно приєднались до списку!", Toast.LENGTH_SHORT).show()
                            inviteInfo = null
                        } catch (e: Exception) {
                            Toast.makeText(context, "Помилка приєднання: ${e.message}", Toast.LENGTH_SHORT).show()
                            inviteInfo = null
                        }
                    }
                }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { paddingValues ->
                    when {
                        currentUser == null -> {
                            LoginScreen(
                                onGoogleSignIn = {
                                    scope.launch {
                                        signInWithGoogle()
                                    }
                                },
                                paddingValues = paddingValues
                            )
                        }
                        selectedShoppingList != null -> {
                            ShoppingItemsScreen(
                                user = currentUser!!,
                                shoppingList = selectedShoppingList!!,
                                onBack = { selectedShoppingList = null },
                                firestoreRepository = firestoreRepository
                            )
                        }
                        else -> {
                            ShoppingListsScreen(
                                user = currentUser!!,
                                onSignOut = { signOut() },
                                onSelectList = { shoppingList ->
                                    selectedShoppingList = shoppingList
                                },
                                paddingValues = paddingValues,
                                firestoreRepository = firestoreRepository
                            )
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun signInWithGoogle() {
        val webClientId = getString(R.string.default_web_client_id)
        
        try {
            Log.d("GoogleSignIn", "Starting Google Sign-In process...")
            Toast.makeText(this, "Запуск входу через Google...", Toast.LENGTH_SHORT).show()
            
            Log.d("GoogleSignIn", "Using web client ID: $webClientId")
            
            if (webClientId.contains("REPLACE_WITH") || webClientId.isEmpty()) {
                Log.e("GoogleSignIn", "Invalid web client ID! Please configure your Google OAuth client ID in strings.xml")
                Toast.makeText(this, "Помилка: потрібно налаштувати Google OAuth client ID", Toast.LENGTH_LONG).show()
                return
            }

            // Спочатку спробуємо з фільтром авторизованих акаунтів
            Log.d("GoogleSignIn", "Trying with authorized accounts first...")
            var googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .build()

            var request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            try {
                Log.d("GoogleSignIn", "Requesting credentials with authorized accounts...")
                val result = credentialManager.getCredential(
                    request = request,
                    context = this,
                )

                val credential = result.credential
                Log.d("GoogleSignIn", "Received credential, parsing Google ID token...")
                
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken

                Log.d("GoogleSignIn", "Successfully received Google ID token")
                firebaseAuthWithGoogle(googleIdToken)
                return
            } catch (e: GetCredentialException) {
                Log.w("GoogleSignIn", "No authorized accounts found, trying all accounts...")
                
                // Якщо не знайдено авторизованих акаунтів, спробуємо всі акаунти
                googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()

                request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                Log.d("GoogleSignIn", "Requesting credentials from all accounts...")
                val result = credentialManager.getCredential(
                    request = request,
                    context = this,
                )

                val credential = result.credential
                Log.d("GoogleSignIn", "Received credential, parsing Google ID token...")
                
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken

                Log.d("GoogleSignIn", "Successfully received Google ID token")
                firebaseAuthWithGoogle(googleIdToken)
            }

        } catch (e: GetCredentialException) {
            Log.e("GoogleSignIn", "GetCredentialException: ${e.message}", e)
            Log.e("GoogleSignIn", "Error type: ${e.type}")
            Log.e("GoogleSignIn", "Error errorMessage: ${e.errorMessage}")
            
            val errorMessage = when {
                e.message?.contains("No credentials available") == true -> 
                    "Немає доступних облікових записів Google. Додайте обліковий запис Google в налаштуваннях пристрою."
                e.message?.contains("No account found") == true -> 
                    "Не знайдено облікових записів Google"
                e.message?.contains("Sign in was cancelled") == true -> 
                    "Вхід скасовано"
                e.message?.contains("16") == true -> 
                    "Помилка конфігурації Google OAuth. Перевірте SHA-1 відбитки."
                webClientId.contains("REPLACE_WITH") -> 
                    "Помилка конфігурації: потрібно налаштувати Google OAuth"
                else -> 
                    "Помилка входу: ${e.message} (type: ${e.type})"
            }
            
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("GoogleSignIn", "GoogleIdTokenParsingException: ${e.message}", e)
            Toast.makeText(this, "Помилка обробки токену Google", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Unexpected error: ${e.message}", e)
            Toast.makeText(this, "Непередбачена помилка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("GoogleSignIn", "signInWithCredential:success")
                    
                    // Створюємо або оновлюємо користувача в Firestore
                    lifecycleScope.launch {
                        val result = firestoreRepository.createOrUpdateUser()
                        result.fold(
                            onSuccess = { user ->
                                Log.d("Firestore", "User created/updated in Firestore: ${user.uid}")
                                Toast.makeText(this@MainActivity, "Вхід успішний!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                Log.e("Firestore", "Failed to create/update user in Firestore", error)
                                Toast.makeText(this@MainActivity, "Помилка збереження даних", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } else {
                    Log.w("GoogleSignIn", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Помилка автентифікації", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun signOut() {
        auth.signOut()
        Log.d("SignOut", "Користувач вийшов із системи")
        Toast.makeText(this, "Вихід успішний", Toast.LENGTH_SHORT).show()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            Log.d("MainActivity", "Received deep link: $data")
            Log.d("MainActivity", "Scheme: ${data.scheme}, Host: ${data.host}, Path: ${data.path}")
            
            // Обробляємо запрошення для різних схем
            val isInviteLink = when {
                // https://divist.app/invite або http://divist.app/invite
                (data.scheme == "https" || data.scheme == "http") && 
                data.host == "divist.app" && 
                data.pathSegments.contains("invite") -> true
                
                // divist://invite
                data.scheme == "divist" && data.host == "invite" -> true
                
                // divist-app://...
                data.scheme == "divist-app" -> true
                
                // divist://... (загальна схема)
                data.scheme == "divist" && data.pathSegments.contains("invite") -> true
                
                else -> false
            }
            
            if (isInviteLink) {
                val listId = data.getQueryParameter("listId")
                val token = data.getQueryParameter("token")
                
                if (listId != null && token != null) {
                    Log.d("MainActivity", "Processing invite: listId=$listId, token=$token")
                    Toast.makeText(this, "Обробляємо запрошення до списку...", Toast.LENGTH_SHORT).show()
                    
                    // Якщо користувач не авторизований, збережемо інформацію про запрошення
                    if (auth.currentUser == null) {
                        // TODO: Зберегти в SharedPreferences або передати через стан
                        Toast.makeText(this, "Спочатку увійдіть в акаунт для приєднання до списку", Toast.LENGTH_LONG).show()
                    } else {
                        // Користувач авторизований, можемо одразу приєднувати
                        lifecycleScope.launch {
                            try {
                                val result = firestoreRepository.joinShoppingListByToken(listId, token)
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(this@MainActivity, "Успішно приєднались до списку!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(this@MainActivity, "Помилка приєднання: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Помилка приєднання: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Невірне посилання запрошення", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("MainActivity", "Unknown deep link format: $data")
            }
        }
    }

    // ...existing code...
}

@Composable
fun LoginScreen(
    onGoogleSignIn: () -> Unit,
    paddingValues: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea),
                        Color(0xFF764ba2)
                    )
                )
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Ласкаво просимо",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                Text(
                    text = "Увійдіть за допомогою Google",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
                
                // Google Sign-In Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onGoogleSignIn() },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_gallery), // Замініть на Google logo
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = "Увійти через Google",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    user: FirebaseUser,
    onSignOut: () -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ласкаво просимо!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (user.displayName != null) {
                    Text(
                        text = user.displayName!!,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Text(
                    text = user.email ?: "",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF666666)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Вийти",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    DivistTheme {
        LoginScreen(
            onGoogleSignIn = { },
            paddingValues = PaddingValues(0.dp)
        )
    }
}
