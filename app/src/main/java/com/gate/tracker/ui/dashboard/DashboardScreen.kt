package com.gate.tracker.ui.dashboard

import androidx.compose.ui.platform.LocalContext
import com.gate.tracker.data.ads.AdMobManager
import com.gate.tracker.ui.dashboard.DashboardViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gate.tracker.ui.components.AdMobBanner
import com.gate.tracker.ui.components.SubjectsSummarySection
import com.gate.tracker.ui.components.CountdownCard
import com.gate.tracker.ui.components.ProgressCard
import com.gate.tracker.ui.components.StreakCard
import com.gate.tracker.ui.components.SubjectCard
import com.gate.tracker.ui.components.ContinueStudyingCard
import com.gate.tracker.ui.components.EmptyStateView
import com.gate.tracker.ui.components.TodoCard
import com.gate.tracker.ui.components.TodoDialog
import com.gate.tracker.ui.components.DailyQuestionCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    branchId: Int,
    viewModel: DashboardViewModel,
    onSubjectClick: (Int) -> Unit,
    onProgressClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExtrasClick: () -> Unit
) {
    val selectedBranch by viewModel.selectedBranch.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val totalCompleted by viewModel.totalCompleted.collectAsState()
    val totalChapters by viewModel.totalChapters.collectAsState()
    val completedSubjects by viewModel.completedSubjects.collectAsState()
    val inProgressSubjects by viewModel.inProgressSubjects.collectAsState()
    val notStartedSubjects by viewModel.notStartedSubjects.collectAsState()
    val daysRemaining by viewModel.daysRemaining.collectAsState()
    val motivationalMessage by viewModel.motivationalMessage.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val longestStreak by viewModel.longestStreak.collectAsState()
    val currentBadge by viewModel.currentBadge.collectAsState()
    val continueStudying by viewModel.continueStudying.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val chaptersBySubject by viewModel.chaptersBySubject.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    
    // Remote config data
    val dailyQuestion by viewModel.dailyQuestion.collectAsState()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsState()
    
    // Revision mode state
    val isRevisionMode by viewModel.isRevisionMode.collectAsState()
    val totalRevised by viewModel.totalRevised.collectAsState()
    
    // Todo dialog state
    var showTodoDialog by remember { mutableStateOf(false) }
    
    // Track mode changes for transition animation
    var showModeTransition by remember { mutableStateOf(false) }
    val previousMode = remember { mutableStateOf(isRevisionMode) }
    
    LaunchedEffect(isRevisionMode) {
        if (previousMode.value != isRevisionMode) {
            showModeTransition = true
            previousMode.value = isRevisionMode
        }
    }
    
    // Match status bar color with app bar
    // Match status bar color with app bar
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()
    val appBarColor = if (isRevisionMode) {
        if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF9333EA) else androidx.compose.ui.graphics.Color(0xFFF3E8FF)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val appBarTextColor = if (isRevisionMode) {
        if (isDarkTheme) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF3B0764)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
        
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.statusBarColor = appBarColor.hashCode()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
    }
    
    // Animation visibility state
    var cardsVisible by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    // AdMob Manager
    val adMobManager = remember { AdMobManager(context) }
    
    LaunchedEffect(Unit) {
        viewModel.loadDashboard(branchId)
        // Load Ad
        adMobManager.loadInterstitialAd()
        // Trigger card animations with slight delay
        delay(100)
        cardsVisible = true
    }
    

    
    Scaffold(
        topBar = {
            // Theme-matching app bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appBarColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .statusBarsPadding(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title Section
                    Column {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            // CS-themed icon (using code brackets)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    text = "</>",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRevisionMode && isDarkTheme) {
                                        androidx.compose.ui.graphics.Color.White
                                    } else if (isRevisionMode) {
                                        androidx.compose.ui.graphics.Color(0xFF3B0764)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GATE Tracker",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = appBarTextColor
                                )
                                // Show "REVISION ON" instead of branch name when in revision mode
                                if (isRevisionMode) {
                                    Text(
                                        text = "REVISION ON",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = appBarTextColor
                                    )
                                } else {
                                    selectedBranch?.let { branch ->
                                        Text(
                                            text = branch.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = appBarTextColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Action Icons Row
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Extras Icon
                        IconButton(onClick = onExtrasClick) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "Extra Features",
                                tint = appBarTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Settings Icon
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = appBarTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dynamic card ordering based on exam urgency
                val isUrgent = daysRemaining <= 60
                
                if (isUrgent) {
                    // URGENCY MODE (≤60 days): Daily Question → Countdown → Goal → Progress → Continue → Streak
                    
                    // 0. Daily Question Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            dailyQuestion?.let { question ->
                                DailyQuestionCard(
                                    question = question.question,
                                    options = question.options,
                                    correctAnswer = question.correctAnswer,
                                    explanation = question.explanation
                                )
                            } ?: DailyQuestionCard() // Fallback to placeholder
                        }
                    }
                    
                    // 1. Countdown Card (urgent deadline)
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 50)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            CountdownCard(
                                daysRemaining = daysRemaining,
                                motivationalMessage = motivationalMessage
                            )
                        }
                    }
                    
                    // 2. Todo Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            TodoCard(
                                todos = todos,
                                pendingCount = pendingCount,
                                onToggleTodo = { id, completed -> viewModel.toggleTodo(id, completed) },
                                onAddClick = { showTodoDialog = true },
                                onShowAllClick = { showTodoDialog = true }
                            )
                        }
                    }

                    // AdMob Banner (Testing)
                    item {
                         com.gate.tracker.ui.components.AdMobBanner()
                    }
                    
                    // 3. Progress Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            ProgressCard(
                                completedChapters = if (isRevisionMode) totalRevised else totalCompleted,
                                totalChapters = totalChapters,
                                onClick = onProgressClick,
                                isRevisionMode = isRevisionMode
                            )
                        }
                    }
                    
                    // 4. Continue Studying
                    continueStudying?.let { data ->
                        item {
                            AnimatedVisibility(
                                visible = cardsVisible,
                                enter = fadeIn(animationSpec = tween(400, delayMillis = 300)) + 
                                        slideInVertically(initialOffsetY = { it / 2 })
                            ) {
                                ContinueStudyingCard(
                                    subject = data.subject,
                                    nextChapter = data.nextChapter,
                                    onClick = { onSubjectClick(data.subject.id) }
                                )
                            }
                        }
                    }
                    
                    // 5. Streak Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 350)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            StreakCard(
                                currentStreak = currentStreak,
                                longestStreak = longestStreak,
                                currentBadge = currentBadge
                            )
                        }
                    }
                    
                } else {
                    // GOAL MODE (>60 days): Daily Question → Todo → Progress → Countdown → Continue → Streak
                    
                    // 0. Daily Question Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            dailyQuestion?.let { question ->
                                DailyQuestionCard(
                                    question = question.question,
                                    options = question.options,
                                    correctAnswer = question.correctAnswer,
                                    explanation = question.explanation
                                )
                            } ?: DailyQuestionCard() // Fallback to placeholder
                        }
                    }
                    
                    // 1. Todo Card (focus on targets)
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 50)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            TodoCard(
                                todos = todos,
                                pendingCount = pendingCount,
                                onToggleTodo = { id, completed -> viewModel.toggleTodo(id, completed) },
                                onAddClick = { showTodoDialog = true },
                                onShowAllClick = { showTodoDialog = true }
                            )
                        }
                    }

                    // AdMob Banner (Testing)
                    item {
                         com.gate.tracker.ui.components.AdMobBanner()
                    }
                    
                    // 2. Progress Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            ProgressCard(
                                completedChapters = if (isRevisionMode) totalRevised else totalCompleted,
                                totalChapters = totalChapters,
                                onClick = onProgressClick,
                                isRevisionMode = isRevisionMode
                            )
                        }
                    }
                    
                    // 3. Countdown Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            CountdownCard(
                                daysRemaining = daysRemaining,
                                motivationalMessage = motivationalMessage
                            )
                        }
                    }
                    
                    // 4. Continue Studying
                    continueStudying?.let { data ->
                        item {
                            AnimatedVisibility(
                                visible = cardsVisible,
                                enter = fadeIn(animationSpec = tween(400, delayMillis = 300)) + 
                                        slideInVertically(initialOffsetY = { it / 2 })
                            ) {
                                ContinueStudyingCard(
                                    subject = data.subject,
                                    nextChapter = data.nextChapter,
                                    onClick = { onSubjectClick(data.subject.id) }
                                )
                            }
                        }
                    }
                    
                    // 5. Streak Card
                    item {
                        AnimatedVisibility(
                            visible = cardsVisible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 350)) + 
                                    slideInVertically(initialOffsetY = { it / 2 })
                        ) {
                            StreakCard(
                                currentStreak = currentStreak,
                                longestStreak = longestStreak,
                                currentBadge = currentBadge
                            )
                        }
                    }

                }
        }
    }
    
    // Todo Dialog
    if (showTodoDialog) {
        TodoDialog(
            todos = todos,
            subjects = subjects,
            chapters = chaptersBySubject,
            recommendations = recommendations,
            existingChapterIds = viewModel.getExistingChapterIds(),
            onAddChapter = { viewModel.addChapterToTodo(it) },
            onToggleTodo = { id, completed -> viewModel.toggleTodo(id, completed) },
            onDeleteTodo = { viewModel.deleteTodo(it) },
            onDismiss = { showTodoDialog = false }
        )
    }
    
    // Color sweep transition when mode changes
    if (showModeTransition) {
        com.gate.tracker.ui.components.ColorSweepTransition(
            fromColor = if (isRevisionMode) 
                if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF667eea) else androidx.compose.ui.graphics.Color(0xFFF3E8FF)
            else 
                androidx.compose.ui.graphics.Color(0xFF9333EA),
            toColor = if (isRevisionMode) 
                if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF7C3AED) else androidx.compose.ui.graphics.Color(0xFFEEE4FF)
            else 
                androidx.compose.ui.graphics.Color(0xFF764ba2),
            onComplete = { showModeTransition = false }
        )
    }
}





