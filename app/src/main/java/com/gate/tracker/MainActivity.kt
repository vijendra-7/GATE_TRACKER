package com.gate.tracker

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gate.tracker.notifications.NotificationHelper
import com.gate.tracker.notifications.NotificationScheduler
import com.gate.tracker.ui.branch.BranchSelectionScreen
import com.gate.tracker.ui.branch.BranchSelectionViewModel
import com.gate.tracker.ui.dashboard.DashboardScreen
import com.gate.tracker.ui.dashboard.DashboardViewModel
import com.gate.tracker.ui.examdate.ExamDateViewModel
import com.gate.tracker.ui.settings.BackupRestoreViewModel
import com.gate.tracker.ui.settings.SettingsScreen
import com.gate.tracker.ui.subject.SubjectDetailScreen
import com.gate.tracker.ui.subject.SubjectDetailViewModel
import com.gate.tracker.ui.subjects.SubjectsOverviewScreen
import com.gate.tracker.ui.theme.GateExamTrackerTheme
import com.gate.tracker.data.local.GateDatabase
import com.gate.tracker.data.repository.GateRepository

// Data class for share dialog
data class ShareDialogData(
    val branchName: String,
    val completedChapters: Int,
    val totalChapters: Int,
    val currentStreak: Int,
    val daysUntilExam: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize notification channels
        NotificationHelper(this).createNotificationChannels()
        
        enableEdgeToEdge()
        
        val app = application as GateApp
        val repository = app.repository
        
        // Initialize BackupRestoreViewModel
        val backupRestoreViewModel = BackupRestoreViewModel(repository, applicationContext)
        
        // Google Sign-In launcher for backup/restore
        var navController: androidx.navigation.NavHostController? = null
        var isSignedInState: androidx.compose.runtime.MutableState<Boolean>? = null
        
        // Notification permission launcher (Android 13+)
        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d("GATE_TRACKER", "Notification permission granted")
            } else {
                Log.d("GATE_TRACKER", "Notification permission denied")
                // User can still use the app, but won't get notifications
            }
        }
        
        // Check and request notification permission for Android 13+
        fun checkNotificationPermission() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                when {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                        Log.d("GATE_TRACKER", "Notification permission already granted")
                    }
                    else -> {
                        Log.d("GATE_TRACKER", "Requesting notification permission")
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
        
        val signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("GATE_TRACKER", "Sign-in result received, resultCode: ${result.resultCode}")
            
            // Handle the sign-in result and wait for success callback
            backupRestoreViewModel.handleSignInResult(
                data = result.data,
                onSuccess = {
                    Log.d("GATE_TRACKER", "Sign-in successful, processing post-login logic")
                    
                    // After sign-in, check if successful and auto-restore
                    lifecycleScope.launch {
                        val driveManager = com.gate.tracker.data.drive.DriveManager(this@MainActivity)
                        val signedIn = driveManager.isSignedIn()
                        Log.d("GATE_TRACKER", "After sign-in check: isSignedIn = $signedIn")
                        
                        if (signedIn) {
                            // Update sign-in state to trigger recomposition
                            isSignedInState?.value = true
                            Log.d("GATE_TRACKER", "Updated isSignedInState to true")
                            
                            // Sync user to Supabase (fire-and-forget, non-blocking)
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                                    if (account?.email != null) {
                                        repository.syncUserToSupabase(
                                            email = account.email!!,
                                            displayName = account.displayName,
                                            photoUrl = account.photoUrl?.toString()
                                        )
                                        Log.d("GATE_TRACKER", "User synced to Supabase: ${account.email}")
                                    }
                                } catch (e: Exception) {
                                    // Silently fail - don't block app launch
                                    Log.e("GATE_TRACKER", "Failed to sync user (non-critical): ${e.message}")
                                }
                            }

                            // Auto-restore from cloud backups (with smart merge)
                            Log.d("GATE_TRACKER", "Starting auto-restore...")
                            // We need to use backupRestoreViewModel for this since it has the methods
                            backupRestoreViewModel.checkAndAutoRestore { success, message ->
                                Log.d("GATE_TRACKER", "Auto-restore completed: success=$success, message=$message")
                                
                                // Navigate to tour or branch selection
                                lifecycleScope.launch {
                                    // Re-fetch preference to ensure we have latest isFirstLaunch status 
                                    // (though unlikely to change during restore unless restore updates it)
                                    val userPref = repository.getUserPreference().firstOrNull()
                                    
                                    if (userPref?.isFirstLaunch == true) {
                                        navController?.navigate("app_tour") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController?.navigate("branch_selection") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                    // Request notification permission after login
                                    checkNotificationPermission()
                                }
                            }
                        } else {
                            Log.d("GATE_TRACKER", "Sign-in failed check after success callback")
                        }
                    }
                },
                onFailure = { error ->
                    Log.e("GATE_TRACKER", "Sign-in failed with error: $error")
                    // Optional: Show toast or error message
                }
            )
        }
        
        // Initialize notification preferences and log app open
        lifecycleScope.launch(Dispatchers.IO) {
            repository.initializeNotificationPreferences()
            
            // Log app open for adaptive notifications
            repository.logActivity(com.gate.tracker.data.local.entity.ActivityType.APP_OPEN)
            
            // Schedule all notifications
            NotificationScheduler.scheduleAll(this@MainActivity)
            
            // Schedule daily question refresh worker
            scheduleDailyQuestionRefresh()
        }
        
        setContent {
            // Get user preference to determine theme
            val app = application as GateApp
            val repository = app.repository
            val userPrefViewModel: UserPreferenceViewModel = viewModel(
                factory = UserPreferenceViewModelFactory(repository)
            )
            val userPref by userPrefViewModel.userPreference.collectAsState()
            
            GateExamTrackerTheme(
                themeMode = userPref?.themeMode ?: 0
            ) {
                val navCtrl = rememberNavController()
                
                // Remember if we've already navigated based on saved preference
                val hasNavigated = rememberSaveable { mutableStateOf(false) }
                val isBranchSelected = userPref?.isFirstLaunch == false && userPref?.selectedBranchId != null && userPref?.selectedBranchId != 0
                
                // Check if user is signed in to Google Drive - use mutableState so it can be updated
                val driveManager = remember { com.gate.tracker.data.drive.DriveManager(this@MainActivity) }
                var isSignedIn by androidx.compose.runtime.remember { mutableStateOf(driveManager.isSignedIn()) }
                
                // Connect to outer variables for sign-in callback
                androidx.compose.runtime.DisposableEffect(Unit) {
                    navController = navCtrl
                    isSignedInState = mutableStateOf(isSignedIn)
                    onDispose { }
                }
                
                // Update outer isSignedInState when local isSignedIn changes
                androidx.compose.runtime.LaunchedEffect(isSignedIn) {
                    isSignedInState?.value = isSignedIn
                }
                
                // Navigate to saved branch on first load (only once)
                androidx.compose.runtime.LaunchedEffect(userPref, isSignedIn) {
                    if (!hasNavigated.value && userPref != null) {
                        // Only navigate to dashboard if signed in AND branch selected AND not first launch
                        // However, if it's first launch, we want to stay/go to app_tour
                        if (isSignedIn && isBranchSelected) {
                            Log.d("GATE_TRACKER", "Navigating to saved branch: ${userPref?.selectedBranchId}")
                            navCtrl.navigate("dashboard/${userPref?.selectedBranchId}") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                        hasNavigated.value = true
                    }
                }
                
                if (userPref == null) {
                    BrandedLoadingScreen()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                    
                    // Determine start destination
                    val startDest = if (isSignedIn) {
                        if (userPref?.isFirstLaunch == true) "app_tour" else "branch_selection"
                    } else {
                        "login"
                    }

                    NavHost(
                        navController = navCtrl,
                        startDestination = startDest
                    ) {
                        // Login Screen Route
                        composable("login") {
                            com.gate.tracker.ui.login.LoginScreen(
                                onSignInClick = {
                                    signInLauncher.launch(backupRestoreViewModel.getSignInIntent())
                                }
                            )
                        }
                        
                        composable("branch_selection") {
                            val viewModel: BranchSelectionViewModel = viewModel(
                                factory = BranchSelectionViewModelFactory(repository)
                            )

                            
                            // We need a scope to check DB.
                            val scope = rememberCoroutineScope()
                            
                            
                            BranchSelectionScreen(
                                viewModel = viewModel,
                                onContinue = {
                                    val branchId = viewModel.selectedBranch.value?.id ?: 1
                                    scope.launch {
                                        val hasProgress = repository.hasAnyProgress(branchId)
                                        if (hasProgress) {
                                            navCtrl.navigate("dashboard/$branchId") {
                                                popUpTo("branch_selection") { inclusive = true }
                                            }
                                        } else {
                                            navCtrl.navigate("onboarding_completion/$branchId") {
                                                popUpTo("branch_selection") { inclusive = true }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        composable("app_tour") {
                            com.gate.tracker.ui.onboarding.AppTourScreen(
                                onGetStarted = {
                                    // Mark first launch as false
                                    userPrefViewModel.updateFirstLaunch(false)
                                    
                                    navCtrl.navigate("branch_selection") {
                                        popUpTo("app_tour") { inclusive = true }
                                    }
                                },
                                onSkip = {
                                    // Mark first launch as false
                                    userPrefViewModel.updateFirstLaunch(false)
                                    
                                    navCtrl.navigate("branch_selection") {
                                        popUpTo("app_tour") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "onboarding_completion/{branchId}",
                            arguments = listOf(navArgument("branchId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val branchId = backStackEntry.arguments?.getInt("branchId") ?: 1
                            val viewModel: com.gate.tracker.ui.onboarding.OnboardingViewModel = viewModel(
                                factory = com.gate.tracker.ui.onboarding.OnboardingViewModelFactory(repository, branchId)
                            )
                            
                            com.gate.tracker.ui.onboarding.OnboardingCompletionScreen(
                                viewModel = viewModel,
                                onContinue = {
                                    navCtrl.navigate("dashboard/$branchId") {
                                        popUpTo("onboarding_completion/$branchId") { inclusive = true }
                                    }
                                },
                                onSkip = {
                                    navCtrl.navigate("dashboard/$branchId") {
                                        popUpTo("onboarding_completion/$branchId") { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        composable(
                            route = "dashboard/{branchId}",
                            arguments = listOf(navArgument("branchId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val rawBranchId = backStackEntry.arguments?.getInt("branchId")
                            Log.d("GATE_TRACKER", "Dashboard route - raw branchId from navigation: $rawBranchId")
                            
                            val branchId = rawBranchId?.let { 
                                if (it == 0) 1 else it 
                            } ?: 1
                            
                            Log.d("GATE_TRACKER", "Dashboard route - final branchId after fallback logic: $branchId")
                            
                            val dashboardViewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModelFactory(application, repository, backupRestoreViewModel)
                            )
                            val calendarViewModel: com.gate.tracker.ui.calendar.ProgressCalendarViewModel = viewModel(
                                factory = ProgressCalendarViewModelFactory(repository, branchId)
                            )
                            
                            // Swipeable Dashboard with Calendar
                            SwipeableDashboard(
                                branchId = branchId,
                                dashboardViewModel = dashboardViewModel,
                                calendarViewModel = calendarViewModel,
                                onSubjectClick = { subjectId ->
                                   navCtrl.navigate("subject/$subjectId")
                                },
                                onProgressClick = {
                                    navCtrl.navigate("subjects_overview/$branchId")
                                },
                                onSettingsClick = {
                                    navCtrl.navigate("settings")
                                },
                                onExtrasClick = {
                                    navCtrl.navigate("extras/$branchId")
                                }
                            )
                        }
                        
                        composable(
                            route = "subjects_overview/{branchId}",
                            arguments = listOf(navArgument("branchId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val branchId = backStackEntry.arguments?.getInt("branchId")?.let { 
                                if (it == 0) 1 else it 
                            } ?: 1
                            val viewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModelFactory(application, repository, backupRestoreViewModel)
                            )
                            SubjectsOverviewScreen(
                                branchId = branchId,
                                viewModel = viewModel,
                                onSubjectClick = { subjectId ->
                                    navCtrl.navigate("subject/$subjectId")
                                },
                                onBackClick = { navCtrl.popBackStack() }
                            )
                        }
                        
                        composable(
                            route = "subject/{subjectId}",
                            arguments = listOf(navArgument("subjectId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val subjectId = backStackEntry.arguments?.getInt("subjectId") ?: 1
                            val viewModel: SubjectDetailViewModel = viewModel(
                                factory = SubjectDetailViewModelFactory(repository, applicationContext)
                            )
                            SubjectDetailScreen(
                                subjectId = subjectId,
                                viewModel = viewModel,
                                repository = repository,
                                onBackClick = { navCtrl.popBackStack() }
                            )
                        }
                        
                        composable("settings") {
                            val coroutineScope = rememberCoroutineScope()
                            val branchId = userPref?.selectedBranchId ?: 1
                            val viewModel: ExamDateViewModel = viewModel(
                                factory = ExamDateViewModelFactory(repository, branchId)
                            )
                            
                            // Helper function to get branch name
                            val branchName = when (branchId) {
                                1 -> "CS"
                                2 -> "EC"
                                3 -> "EE"
                                4 -> "ME"
                                5 -> "CE"
                                6 -> "DA"
                                else -> "CS"
                            }
                            
                            SettingsScreen(
                                examDateViewModel = viewModel,
                                backupRestoreViewModel = backupRestoreViewModel,
                                branchId = branchId,
                                branchName = branchName,
                                onBackClick = { navCtrl.popBackStack() },
                                onNavigateToNotifications = {
                                    navCtrl.navigate("notification_settings")
                                },
                                onSignOut = {
                                    coroutineScope.launch {
                                        // Clear question history to prevent it from persisting across different Google accounts
                                        repository.questionHistoryRepository.clearHistory()
                                        repository.clearSelectedBranch()
                                        navCtrl.navigate("branch_selection") {
                                            popUpTo(navCtrl.graph.startDestinationId) { 
                                                inclusive = true 
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onResetData = {
                                    coroutineScope.launch {
                                        // Get the current branch ID from user preferences
                                        val branchId = userPref?.selectedBranchId ?: 1
                                        repository.resetAllProgress(branchId)
                                    }
                                },
                                onLaunchSignIn = {
                                    signInLauncher.launch(backupRestoreViewModel.getSignInIntent())
                                },
                                selectedTheme = userPref?.themeMode ?: 0,
                                onThemeChange = { mode -> 
                                    userPrefViewModel.updateTheme(mode)
                                }
                            )
                        }
                        
                        composable(
                            route = "extras/{branchId}",
                            arguments = listOf(navArgument("branchId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val branchId = backStackEntry.arguments?.getInt("branchId") ?: 1
                            val dashboardViewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModelFactory(application, repository, backupRestoreViewModel)
                            )
                            
                            // Load dashboard data
                            LaunchedEffect(branchId) {
                                dashboardViewModel.loadDashboard(branchId)
                            }
                            
                            // Collect dashboard state
                            val branch by dashboardViewModel.selectedBranch.collectAsState()
                            val completed by dashboardViewModel.totalCompleted.collectAsState()
                            val total by dashboardViewModel.totalChapters.collectAsState()
                            val streak by dashboardViewModel.currentStreak.collectAsState()
                            val days by dashboardViewModel.daysRemaining.collectAsState()
                            
                            var showShareDialog by remember { mutableStateOf(false) }
                            var showRevisionDialog by remember { mutableStateOf(false) }
                            val isRevisionMode by repository.isRevisionMode().collectAsState(initial = false)
                            
                            com.gate.tracker.ui.extras.ExtraFeaturesScreen(
                                onBackClick = { navCtrl.popBackStack() },
                                onMockTestsClick = {
                                    navCtrl.navigate("mock_tests/$branchId")
                                },
                                branchId = branchId,
                                onShareProgressClick = { showShareDialog = true },
                                onRevisionModeClick = { showRevisionDialog = true },
                                onMarkPreviousClick = {
                                    navCtrl.navigate("onboarding_completion/$branchId")
                                }
                            )
                            
                            if (showShareDialog) {
                                com.gate.tracker.ui.components.ShareProgressDialog(
                                    branchName = branch?.name ?: "",
                                    completedChapters = completed,
                                    totalChapters = total,
                                    currentStreak = streak,
                                    daysUntilExam = days,
                                    onDismiss = { showShareDialog = false },
                                    isRevisionMode = isRevisionMode
                                )
                            }
                            
                            
                            var showModeTransition by remember { mutableStateOf(false) }
                            var transitionToRevisionMode by remember { mutableStateOf(false) }
                            
                            if (showRevisionDialog) {
                                com.gate.tracker.ui.components.RevisionModeDialog(
                                    isCurrentlyEnabled = isRevisionMode,
                                    onDismiss = { showRevisionDialog = false },
                                    onToggle = { enabled ->
                                        lifecycleScope.launch {
                                            repository.setRevisionMode(enabled)
                                            showRevisionDialog = false
                                            
                                            // Show transition animation
                                            transitionToRevisionMode = enabled
                                            showModeTransition = true
                                            
                                            // Wait for animation to start
                                            kotlinx.coroutines.delay(200)
                                            
                                            // Navigate to dashboard to show mode change
                                            navCtrl.navigate("dashboard/$branchId") {
                                                popUpTo("extras/$branchId") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            
                            // Color sweep transition overlay
                            if (showModeTransition) {
                                com.gate.tracker.ui.components.ColorSweepTransition(
                                    fromColor = if (transitionToRevisionMode) 
                                        androidx.compose.ui.graphics.Color(0xFF667eea) 
                                    else 
                                        androidx.compose.ui.graphics.Color(0xFFf09819),
                                    toColor = if (transitionToRevisionMode) 
                                        androidx.compose.ui.graphics.Color(0xFFff512f) 
                                    else 
                                        androidx.compose.ui.graphics.Color(0xFF764ba2),
                                    onComplete = { showModeTransition = false }
                                )
                            }
                        }
                        
                        composable(
                            route = "mock_tests/{branchId}",
                            arguments = listOf(navArgument("branchId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val branchId = backStackEntry.arguments?.getInt("branchId") ?: 1
                            val mockTestViewModel: com.gate.tracker.ui.mocktest.MockTestViewModel = viewModel(
                                factory = MockTestViewModelFactory(repository, branchId)
                            )
                            com.gate.tracker.ui.mocktest.MockTestsScreen(
                                viewModel = mockTestViewModel,
                                onBackClick = { navCtrl.popBackStack() }
                            )
                        }
                        
                        composable("notification_settings") {
                            val notificationViewModel: com.gate.tracker.ui.notifications.NotificationSettingsViewModel = viewModel(
                                factory = com.gate.tracker.ui.notifications.NotificationSettingsViewModelFactory(application, repository)
                            )
                            com.gate.tracker.ui.notifications.NotificationSettingsScreen(
                                viewModel = notificationViewModel,
                                onNavigateBack = { navCtrl.popBackStack() }
                            )
                        }
                    }
                    } // End of NavHost
                        
                        // Overlay loading screen ONLY if we are supposed to auto-navigate 
                        // and haven't finished doing so yet.
                        AnimatedVisibility(
                            visible = isBranchSelected && !hasNavigated.value,
                            exit = fadeOut(animationSpec = tween(500))
                        ) {
                            BrandedLoadingScreen()
                        }
                    } // End of Box
                } // End of else
        }
    }
    
    /**
     * Schedule daily question refresh using WorkManager
     * Runs every 24 hours to prefetch next 7 days of questions
     */
    private fun scheduleDailyQuestionRefresh() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED) // Requires any network
            .setRequiresBatteryNotLow(true) // Don't run when battery is low
            .build()
        
        val dailyWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.gate.tracker.workers.DailyQuestionRefreshWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.gate.tracker.workers.DailyQuestionRefreshWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already exists
            dailyWorkRequest
        )
        
        Log.d("GATE_TRACKER", "Daily question refresh worker scheduled")
    }
}

@androidx.compose.runtime.Composable
fun BrandedLoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1A1C1E)), // Always Dark to match Splash
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Use the actual app logo drawable to match System Splash exactly
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "App Logo",
            modifier = Modifier.size(240.dp) // Adjusted to be significantly larger based on user feedback
        )
    }
}
