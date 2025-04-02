package com.example.augmented_mobile_application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
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

class MainActivity : ComponentActivity() {
    private val userViewModel: UserViewModel by viewModels()
    private val manualViewModel: ManualViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Augmented_mobile_applicationTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginView(
                                navController = navController,
                                userViewModel = userViewModel
                            )
                        }
                        composable("userContent") {
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
                        // Add routes for viewing the manual
                        composable("manualView/{pdfName}") { backStackEntry ->
                            val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "pump"
                            ManualView(
                                navController = navController,
                                manualViewModel = manualViewModel,
                                pdfName = pdfName
                            )
                        }
                        // Add direct route to pump manual
                        composable("pumpManual") {
                            ManualView(
                                navController = navController,
                                manualViewModel = manualViewModel,
                                pdfName = "pump/pump"  // Path to the PDF in assets folder
                            )
                        }
                        // AR view will be integrated later
                        composable("arView/{machineName}") { backStackEntry ->
                            // Placeholder for AR view
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
        }
    }
}
