package com.example.augmented_mobile_application

import android.os.Bundle
import android.util.Log
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.augmented_mobile_application.core.ResourcePool
import com.example.augmented_mobile_application.ui.LoginView
import com.example.augmented_mobile_application.ui.UserContentView
import com.example.augmented_mobile_application.ui.ManualView
import com.example.augmented_mobile_application.ui.ARView
import com.example.augmented_mobile_application.ui.PumpManualsView
import com.example.augmented_mobile_application.viewmodel.UserViewModel
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.ui.theme.Augmented_mobile_applicationTheme
import com.example.augmented_mobile_application.model.LoginEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.augmented_mobile_application.opencv.OpenCVInitializer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private val userViewModel: UserViewModel by viewModels()
    private val manualViewModel: ManualViewModel by viewModels()
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV using our helper class
        initializeOpenCV()
        
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
    
    private fun initializeOpenCV() {
        try {
            // Try to load OpenCV library
            System.loadLibrary("opencv_java4")
            Log.d(TAG, "OpenCV native library loaded successfully")
            
            // Initialize OpenCV synchronously for immediate availability
            val success = OpenCVInitializer.initSync()
            if (success) {
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed, trying async method")
                // Try asynchronous initialization as fallback
                OpenCVInitializer.initAsync(this) {
                    Log.d(TAG, "OpenCV initialized asynchronously")
                }
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load OpenCV native library: ${e.message}")
            // Try asynchronous initialization as fallback
            OpenCVInitializer.initAsync(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenCV initialization: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        try {
            ResourcePool.getInstance().shutdown()
            Log.i("MainActivity", "ResourcePool shutdown completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error shutting down ResourcePool", e)
        }
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

            // New route for Pump Manuals selection
            composable(route = "pumpManuals") {
                PumpManualsView(navController = navController)
            }
            
            // Manual view route - Decode the pdfName argument
            composable(route = "manualView/{pdfName}") { backStackEntry ->
                val encodedPdfName = backStackEntry.arguments?.getString("pdfName") ?: "pump"
                // Decode the pdfName argument
                val pdfName = URLDecoder.decode(encodedPdfName, StandardCharsets.UTF_8.toString())
                ManualView(
                    navController = navController,
                    manualViewModel = manualViewModel,
                    pdfName = pdfName
                )
            }

            // AR view with dynamic GLB path support
            composable(
                route = "arView/{machineName}?glbPath={glbPath}&routineId={routineId}",
                arguments = listOf(
                    navArgument("machineName") { type = NavType.StringType },
                    navArgument("glbPath") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("routineId") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val machineName = backStackEntry.arguments?.getString("machineName") ?: "Bomba Centrifuga"
                val glbPath = backStackEntry.arguments?.getString("glbPath")
                val routineId = backStackEntry.arguments?.getString("routineId")
                
                ARView(
                    machine_selected = machineName,
                    navController = navController,
                    glbPath = glbPath,
                    routineId = routineId
                )
            }
        }
    }
}
