package com.example.augmented_mobile_application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.augmented_mobile_application.ui.LoginView
import com.example.augmented_mobile_application.ui.UserContentView
import com.example.augmented_mobile_application.ui.ManualView
import com.example.augmented_mobile_application.viewmodel.UserViewModel
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.ui.theme.Augmented_mobile_applicationTheme
import com.example.augmented_mobile_application.model.LoginEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val userViewModel: UserViewModel by viewModels()
    private val manualViewModel: ManualViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Observe app lifecycle and perform necessary actions
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                // Initialize resources, observers, etc.
            }
        }
        
        setContent {
            Augmented_mobile_applicationTheme {
                MainAppContent(userViewModel, manualViewModel)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
    }
}

@Composable
fun MainAppContent(userViewModel: UserViewModel, manualViewModel: ManualViewModel) {
    val navController = rememberNavController()
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = "login") {
                LoginView(
                    navController = navController,
                    userViewModel = userViewModel
                )
            }
            
            composable(route = "userContent") {
                UserContentView(
                    navController = navController,
                    userViewModel = userViewModel,
                    manualViewModel = manualViewModel,
                    onLogout = {
                        userViewModel.handleEvent(LoginEvent.OnLogout)
                        navController.navigate("login") {
                            popUpTo("userContent") { inclusive = true }
                        }
                    }
                )
            }
            
            // Manual view route
            composable(route = "manualView/{pdfName}") { backStackEntry ->
                val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "pump"
                ManualView(
                    navController = navController,
                    manualViewModel = manualViewModel,
                    pdfName = pdfName
                )
            }

            // AR view 
            composable(route = "arView/{machineName}") { backStackEntry ->
                // Placeholder for AR view (will be implemented later)
                UserContentView(
                    navController = navController,
                    userViewModel = userViewModel,
                    manualViewModel = manualViewModel,
                    onLogout = {
                        userViewModel.handleEvent(LoginEvent.OnLogout)
                        navController.navigate("login") {
                            popUpTo("userContent") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
