package com.vendistri.operations.features.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vendistri.operations.components.BackButton
import com.vendistri.operations.components.PrimaryActionButton
import com.vendistri.operations.components.VendistriLogo
import com.vendistri.operations.design.AppColors
import com.vendistri.operations.network.NetworkConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

private const val PasswordResetCooldownSeconds = 60L
private const val AuthPreferencesName = "vendistri_auth"
private const val LastEmailKey = "last_email"
private const val PasswordResetLastEmailKey = "password_reset_last_email"

@Composable
fun AuthView(
    uiState: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onPasswordResetRequested: suspend (email: String) -> Boolean
) {
    var route by remember { mutableStateOf<AuthRoute>(AuthRoute.SignIn) }

    when (val currentRoute = route) {
        AuthRoute.SignIn -> AuthSignInView(
            uiState = uiState,
            onSignIn = onSignIn,
            onForgotPassword = { email -> route = AuthRoute.ForgotPassword(email) }
        )
        is AuthRoute.ForgotPassword -> ForgotPasswordView(
            initialEmail = currentRoute.initialEmail,
            onBack = { route = AuthRoute.SignIn },
            onPasswordResetRequested = onPasswordResetRequested
        )
    }
}

private sealed interface AuthRoute {
    data object SignIn : AuthRoute
    data class ForgotPassword(val initialEmail: String) : AuthRoute
}

@Composable
private fun AuthSignInView(
    uiState: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var email by remember { mutableStateOf(context.authPreferences().getString(LastEmailKey, "").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AuthScaffold {
        AuthLogo()
        Spacer(modifier = Modifier.height(56.dp))
        AuthCard {
            AuthCardTitle(title = "Sign In", subtitle = "Access your Vendistri portal")

            AuthTextField(
                label = "Email",
                value = email,
                onValueChange = {
                    email = it
                    localError = null
                },
                keyboardType = KeyboardType.Email
            )
            AuthTextField(
                label = "Password",
                value = password,
                onValueChange = {
                    password = it
                    localError = null
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show", color = AppColors.muted)
                    }
                }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Forgot password",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onForgotPassword(email) }
                )
                Text(
                    text = "New user",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("${NetworkConfig.signupWebUrl}/signup.html")
                    }
                )
            }

            val message = localError ?: uiState.message
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PrimaryActionButton(
                text = if (uiState.isSubmitting) "Signing in..." else "Sign in",
                onClick = {
                    val normalizedEmail = normalizeIdentityEmail(email)
                    if (normalizedEmail.isBlank() || password.isBlank()) {
                        localError = "Email and password are required."
                        return@PrimaryActionButton
                    }
                    email = normalizedEmail
                    context.authPreferences().edit().putString(LastEmailKey, normalizedEmail).apply()
                    onSignIn(normalizedEmail, password)
                },
                enabled = !uiState.isSubmitting
            )
        }
    }
}

@Composable
private fun ForgotPasswordView(
    initialEmail: String,
    onBack: () -> Unit,
    onPasswordResetRequested: suspend (email: String) -> Boolean
) {
    val context = LocalContext.current
    val preferences = context.authPreferences()
    val coroutineScope = rememberCoroutineScope()
    var email by remember {
        mutableStateOf(
            listOf(
                initialEmail,
                preferences.getString(PasswordResetLastEmailKey, "").orEmpty(),
                preferences.getString(LastEmailKey, "").orEmpty()
            ).firstOrNull { it.isNotBlank() }.orEmpty()
        )
    }
    var sentEmail by remember { mutableStateOf<String?>(null) }
    var resetAllowedAtMillis by remember { mutableLongStateOf(restoredResetAllowedAt(context, email)) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val cooldownSeconds = ceil(((resetAllowedAtMillis - nowMillis).coerceAtLeast(0L)) / 1000.0).toInt()
    val isInCooldown = cooldownSeconds > 0

    LaunchedEffect(isInCooldown) {
        while (isInCooldown) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(email) {
        resetAllowedAtMillis = restoredResetAllowedAt(context, email)
        if (resetAllowedAtMillis > System.currentTimeMillis()) {
            sentEmail = normalizeIdentityEmail(email)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        AuthTopBar(title = "Reset Password", onBack = onBack)

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Reset Password",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "We'll email you a secure link to reset your password.",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (sentEmail != null && isInCooldown) {
                Text("Success!", color = AppColors.done, fontWeight = FontWeight.Bold)
                Text(
                    text = "We sent a reset link to ${sentEmail ?: normalizeIdentityEmail(email)}. Check your email and click the link to reset your password.",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Link expires in 30 minutes.", color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (isInCooldown) "Can resend in 0:${cooldownSeconds.toString().padStart(2, '0')}" else "Send another one",
                    color = AppColors.muted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                AuthTextField(
                    label = "Email",
                    value = email,
                    onValueChange = {
                        email = it
                        localError = null
                    },
                    keyboardType = KeyboardType.Email
                )
                PrimaryActionButton(
                    text = if (isSending) "Sending..." else "Send reset link",
                    onClick = {
                        val normalizedEmail = normalizeIdentityEmail(email)
                        if (normalizedEmail.isBlank()) {
                            localError = "Email is required."
                            return@PrimaryActionButton
                        }
                        coroutineScope.launch {
                            isSending = true
                            localError = null
                            val didSend = onPasswordResetRequested(normalizedEmail)
                            isSending = false
                            if (!didSend) {
                                localError = "Could not send a reset link. Please try again."
                                return@launch
                            }
                            email = normalizedEmail
                            sentEmail = normalizedEmail
                            val nextAllowedAt = System.currentTimeMillis() + PasswordResetCooldownSeconds * 1000L
                            resetAllowedAtMillis = nextAllowedAt
                            preferences.edit()
                                .putString(PasswordResetLastEmailKey, normalizedEmail)
                                .putLong(passwordResetCooldownKey(normalizedEmail), nextAllowedAt)
                                .apply()
                        }
                    },
                    enabled = !isSending && !isInCooldown
                )
            }

            if (!localError.isNullOrBlank()) {
                Text(
                    localError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AuthTopBar(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(54.dp)) {
        BackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start,
        content = content
    )
}

@Composable
private fun AuthLogo() {
    VendistriLogo(
        modifier = Modifier
            .fillMaxWidth(0.42f)
            .height(44.dp)
    )
}

@Composable
private fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun AuthCardTitle(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = Color.Black,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(subtitle, color = AppColors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            color = Color.Black,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = keyboardType
            ),
            trailingIcon = trailing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                disabledTextColor = AppColors.muted,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Color.White,
                focusedBorderColor = AppColors.vendBlue,
                unfocusedBorderColor = AppColors.border,
                disabledBorderColor = AppColors.border,
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Black,
                cursorColor = AppColors.vendBlue,
                focusedTrailingIconColor = AppColors.muted,
                unfocusedTrailingIconColor = AppColors.muted
            )
        )
    }
}

private fun Context.authPreferences() = getSharedPreferences(AuthPreferencesName, Context.MODE_PRIVATE)

private fun passwordResetCooldownKey(email: String): String {
    return "password_reset_cooldown:${normalizeIdentityEmail(email)}"
}

private fun restoredResetAllowedAt(context: Context, email: String): Long {
    if (email.isBlank()) return 0L
    return context.authPreferences().getLong(passwordResetCooldownKey(email), 0L)
}
