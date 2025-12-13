package com.gate.tracker.ui.dashboard

import android.app.Application
import android.util.Log

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gate.tracker.data.local.entity.BranchEntity
import com.gate.tracker.data.local.entity.ChapterEntity
import com.gate.tracker.data.local.entity.SubjectEntity
import com.gate.tracker.data.local.entity.TodoWithDetails
import com.gate.tracker.data.model.StreakBadge
import com.gate.tracker.data.repository.GateRepository
import com.gate.tracker.data.repository.RemoteConfigRepository
import com.gate.tracker.data.remote.DailyQuestion
import com.gate.tracker.data.remote.AppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DashboardViewModel(
    application: Application,
    private val repository: GateRepository,
    private val backupRestoreViewModel: com.gate.tracker.ui.settings.BackupRestoreViewModel
) : AndroidViewModel(application) {
    
    // Remote Config
    private val remoteConfigRepository = RemoteConfigRepository()
    
    private val _dailyQuestion = MutableStateFlow<DailyQuestion?>(null)
    val dailyQuestion: StateFlow<DailyQuestion?> = _dailyQuestion.asStateFlow()
    
    private val _appUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val appUpdateInfo: StateFlow<AppUpdateInfo?> = _appUpdateInfo.asStateFlow()
    
    private val _selectedBranch = MutableStateFlow<BranchEntity?>(null)
    val selectedBranch: StateFlow<BranchEntity?> = _selectedBranch.asStateFlow()
    
    private val _subjects = MutableStateFlow<List<SubjectEntity>>(emptyList())
    val subjects: StateFlow<List<SubjectEntity>> = _subjects.asStateFlow()
    
    private val _totalCompleted = MutableStateFlow(0)
    val totalCompleted: StateFlow<Int> = _totalCompleted.asStateFlow()
    
    private val _totalChapters = MutableStateFlow(0)
    val totalChapters: StateFlow<Int> = _totalChapters.asStateFlow()
    
    private val _progressPercentage = MutableStateFlow(0)
    val progressPercentage: StateFlow<Int> = _progressPercentage.asStateFlow()
    
    private val _completedSubjects = MutableStateFlow(0)
    val completedSubjects: StateFlow<Int> = _completedSubjects.asStateFlow()
    
    private val _inProgressSubjects = MutableStateFlow(0)
    val inProgressSubjects: StateFlow<Int> = _inProgressSubjects.asStateFlow()
    
    private val _notStartedSubjects = MutableStateFlow(0)
    val notStartedSubjects: StateFlow<Int> = _notStartedSubjects.asStateFlow()
    
    private val _daysRemaining = MutableStateFlow(0)
    val daysRemaining: StateFlow<Int> = _daysRemaining.asStateFlow()
    
    private val _motivationalMessage = MutableStateFlow("")
    val motivationalMessage: StateFlow<String> = _motivationalMessage.asStateFlow()
    
    data class ContinueStudyingData(
        val subject: SubjectEntity,
        val nextChapter: ChapterEntity
    )
    
    data class Recommendation(
        val subject: SubjectEntity,
        val chapter: ChapterEntity
    )
    
    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()
    
    private val _continueStudying = MutableStateFlow<ContinueStudyingData?>(null)
    val continueStudying: StateFlow<ContinueStudyingData?> = _continueStudying.asStateFlow()
    
    // Todo tracking
    val todos: StateFlow<List<TodoWithDetails>> = MutableStateFlow(emptyList())
    val pendingCount: StateFlow<Int> = MutableStateFlow(0)
    
    // Chapters map for subject selection
    private val _chaptersBySubject = MutableStateFlow<Map<Int, List<ChapterEntity>>>(emptyMap())
    val chaptersBySubject: StateFlow<Map<Int, List<ChapterEntity>>> = _chaptersBySubject.asStateFlow()
    
    // Revision Mode tracking
    val isRevisionMode: StateFlow<Boolean> = repository.isRevisionMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    private val _totalRevised = MutableStateFlow(0)
    val totalRevised: StateFlow<Int> = _totalRevised.asStateFlow()
    
    fun addChapterToTodo(chapterId: Int) {
        viewModelScope.launch {
            _selectedBranch.value?.let { branch ->
                repository.addTodo(chapterId, branch.id, isRevisionMode.value)
            }
        }
    }
    


    fun toggleTodo(todoId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            if (isCompleted) {
                // Check if this is the last item BEFORE it gets deleted
                // We use pendingCount or todos.size. 
                // Since this logic runs before DB update is reflected in UI but AFTER user click.
                val isLastItem = todos.value.size == 1
                
                // First, mark the todo as complete to trigger animation
                repository.toggleTodo(todoId, true)
                
                // Add delay to allow animation to play (400ms for fade + slide animation)
                kotlinx.coroutines.delay(400)
                
                // Marking as complete: mark chapter complete/revised AND remove from to-do list
                val todo = todos.value.find { it.todo.id == todoId }
                todo?.let {
                    if (todo.todo.isRevisionMode) {
                        // Revision mode: mark as revised or increment revision count
                        if (!it.chapter.isRevised) {
                            // First time revision - mark as revised
                            repository.toggleRevisionStatus(it.chapter.id, it.chapter.isRevised)
                            repository.updateSubjectRevisedCount(it.chapter.subjectId)
                        } else {
                            // Already revised - just increment revision count
                            repository.incrementRevisionCount(it.chapter.id)
                        }
                    } else {
                        // Normal mode: mark chapter as complete
                        if (!it.chapter.isCompleted) {
                            repository.toggleChapterStatus(it.chapter.id, it.chapter.isCompleted)
                            repository.updateSubjectCompletedCount(it.chapter.subjectId)
                        }
                    }
                    
                    // Remove from to-do list after animation completes
                    repository.deleteTodo(todoId)
                    

                    
                    // Trigger auto-backup after a delay (3000ms = 3 seconds)
                    // Delay ensures: 1) Animation completes, 2) Database writes finish to avoid conflicts
                    kotlinx.coroutines.delay(3000)
                    _selectedBranch.value?.let { branch ->
                        backupRestoreViewModel.autoBackupSilently(branch.id, branch.name)
                    }
                }
            } else {
                // Unchecking (shouldn't happen with auto-delete, but keep for safety)
                repository.toggleTodo(todoId, isCompleted)
            }
        }
    }
    
    fun deleteTodo(todoId: Int) {
        viewModelScope.launch {
            repository.deleteTodo(todoId)
        }
    }
    
    fun getExistingChapterIds(): Set<Int> {
        return todos.value.map { it.todo.chapterId }.toSet()
    }
    
    fun loadDashboard(branchId: Int) {
        Log.d("GATE_TRACKER", "loadDashboard called with branchId: $branchId")
        viewModelScope.launch {
            // Load branch
            val branch = repository.getBranchById(branchId)
            Log.d("GATE_TRACKER", "Loaded branch: ${branch?.name}")
            _selectedBranch.value = branch
            
            // Load subjects and sort by most recently studied
            repository.getSubjectsByBranch(branchId).collect { subjectList ->
                Log.d("GATE_TRACKER", "Received ${subjectList.size} subjects from database")
                
                // Get last completion/revision dates for all subjects based on mode
                val isRevisionMode = repository.isRevisionMode().first()
                val subjectDates = mutableMapOf<Int, Long>()
                subjectList.forEach { subject ->
                    val lastDate = if (isRevisionMode) {
                        repository.getLastRevisionDateForSubject(subject.id)
                    } else {
                        repository.getLastCompletionDateForSubject(subject.id)
                    }
                    subjectDates[subject.id] = lastDate ?: 0L
                }
                
                // Sort subjects by last completion date (most recent first)
                val sortedSubjects = subjectList.sortedByDescending { subject ->
                    subjectDates[subject.id] ?: 0L
                }
                
                _subjects.value = sortedSubjects
                calculateProgress(sortedSubjects)
            }
        }
        
        loadExamCountdown(branchId)
        loadContinueStudying(branchId)
        loadRevisionProgress(branchId)
        loadTodoData(branchId)
        loadRemoteConfig()
    }
    
    private fun loadRemoteConfig() {
        viewModelScope.launch {
            try {
                val result = remoteConfigRepository.fetchConfig()
                result.onSuccess { config ->
                    _dailyQuestion.value = config.dailyQuestion
                    _appUpdateInfo.value = config.appUpdate
                    Log.d("GATE_TRACKER", "Remote config loaded successfully")
                }.onFailure { error ->
                    Log.e("GATE_TRACKER", "Failed to load remote config", error)
                    // Silently fail - app continues with local data only
                }
            } catch (e: Exception) {
                Log.e("GATE_TRACKER", "Remote config error", e)
            }
        }
    }
    
    private fun loadRevisionProgress(branchId: Int) {
        viewModelScope.launch {
            repository.getTotalRevisedChapters(branchId).collect { revised ->
                _totalRevised.value = revised
            }
        }
    }
    
    private fun calculateProgress(subjects: List<SubjectEntity>) {
        val total = subjects.sumOf { it.totalChapters }
        val completed = subjects.sumOf { it.completedChapters }
        
        _totalChapters.value = total
        _totalCompleted.value = completed
        _progressPercentage.value = if (total > 0) (completed * 100 / total) else 0
        
        // Calculate subject status counts
        val completedSubjects = subjects.count { it.completedChapters == it.totalChapters && it.totalChapters > 0 }
        val inProgressSubjects = subjects.count { it.completedChapters > 0 && it.completedChapters < it.totalChapters }
        val notStartedSubjects = subjects.count { it.completedChapters == 0 }
        
        _completedSubjects.value = completedSubjects
        _inProgressSubjects.value = inProgressSubjects
        _notStartedSubjects.value = notStartedSubjects
    }
    
    private fun loadTodoData(branchId: Int) {
        viewModelScope.launch {
            // Load all to-dos for the current mode - use flatMapLatest to switch flows when mode changes
            isRevisionMode
                .flatMapLatest { revisionMode ->
                    repository.getTodosByBranch(branchId, revisionMode)
                }
                .collect { todoList ->
                    (todos as MutableStateFlow).value = todoList
                }
        }
        
        viewModelScope.launch {
            // Load pending count for current mode - use flatMapLatest to switch flows when mode changes
            isRevisionMode
                .flatMapLatest { revisionMode ->
                    repository.getPendingTodoCount(branchId, revisionMode)
                }
                .collect { count ->
                    (pendingCount as MutableStateFlow).value = count
                }
        }
        
        // Load all chapters grouped by subject for selection dialog
        // This needs to react to subjects changes
        viewModelScope.launch {
            _subjects.collect { subjectList ->
                val chaptersMap = mutableMapOf<Int, List<ChapterEntity>>()
                subjectList.forEach { subject ->
                    val chapters = repository.getChaptersBySubject(subject.id).first()
                    chaptersMap[subject.id] = chapters
                }
                _chaptersBySubject.value = chaptersMap
            }
        }
        
        // Calculate recommendations dynamically
        viewModelScope.launch {
            combine(
                _chaptersBySubject,
                todos,
                isRevisionMode,
                _subjects
            ) { chaptersMap, currentTodos, revisionMode, subjectList ->
                val todoChapterIds = currentTodos.map { it.todo.chapterId }.toSet()
                val recommendations = mutableListOf<Recommendation>()

                subjectList.forEach { subject ->
                    chaptersMap[subject.id]?.let { chapters ->
                        val nextChapter = chapters.firstOrNull { chapter ->
                            val isDone = if (revisionMode) chapter.isRevised else chapter.isCompleted
                            !isDone && !todoChapterIds.contains(chapter.id)
                        }
                        
                        if (nextChapter != null) {
                            recommendations.add(Recommendation(subject, nextChapter))
                        }
                    }
                }
                recommendations.take(3)
            }.collect { recs ->
                _recommendations.value = recs
            }
        }
    }
    
    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()
    
    // Longest streak tracking
    val longestStreak: StateFlow<Int> = repository.getUserPreference()
        .map { it?.longestStreak ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    // Current badge based on current streak
    val currentBadge: StateFlow<StreakBadge> = currentStreak
        .map { StreakBadge.fromStreak(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreakBadge.NONE
        )
    
    // ... existing initialization ...

    private fun loadExamCountdown(branchId: Int) {
        Log.d("GATE_TRACKER", "loadExamCountdown called with branchId: $branchId")
        viewModelScope.launch {
            Log.d("GATE_TRACKER", "loadExamCountdown - getting exam date for branchId: $branchId")
            repository.getExamDate(branchId).collect { examDate ->
                Log.d("GATE_TRACKER", "loadExamCountdown - received exam date: $examDate for branchId: $branchId")
                examDate?.let {
                    val currentTime = System.currentTimeMillis()
                    val days = TimeUnit.MILLISECONDS.toDays(it - currentTime).toInt()
                    Log.d("GATE_TRACKER", "loadExamCountdown - calculated days remaining: $days (examDate: $it, currentTime: $currentTime)")
                    _daysRemaining.value = days
                    _motivationalMessage.value = getMotivationalMessage(days)
                }
            }
        }
        
        // Also load streak data
        viewModelScope.launch {
            repository.getAllCompletedChaptersWithDates(branchId).collect { chapters ->
                calculateStreak(chapters)
            }
        }
    }
    
    private fun calculateStreak(chapters: List<ChapterEntity>) {
        if (chapters.isEmpty()) {
            _currentStreak.value = 0
            return
        }
        
        // Group by date to get unique study days
        val dateKeys = chapters
            .filter { it.completedDate != null }
            .map { com.gate.tracker.util.DateUtils.getDateKey(it.completedDate!!) }
            .distinct()
            .sortedDescending() // Most recent first
            
        if (dateKeys.isEmpty()) {
            _currentStreak.value = 0
            return
        }
        
        val todayKey = com.gate.tracker.util.DateUtils.getDateKey(System.currentTimeMillis())
        val yesterdayKey = com.gate.tracker.util.DateUtils.getDateKey(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        
        var currentStreak = 0
        
        // Check if current streak is active (studied today or yesterday)
        val mostRecentKey = dateKeys.first()
        if (mostRecentKey == todayKey || mostRecentKey == yesterdayKey) {
            currentStreak = 1
            
            // Count consecutive days backwards from most recent
            for (i in 1 until dateKeys.size) {
                val prevDateKey = dateKeys[i - 1]
                val currDateKey = dateKeys[i]
                
                // Check if dates are consecutive (difference of 1 day)
                if (isConsecutiveDays(currDateKey.toLong(), prevDateKey.toLong())) {
                    currentStreak++
                } else {
                    break // Streak broken
                }
            }
        }
        
        _currentStreak.value = currentStreak
        
        // Update longest streak if current streak is a new record
        viewModelScope.launch {
            val currentLongest = longestStreak.value
            if (currentStreak > currentLongest) {
                repository.updateLongestStreak(currentStreak)
            }
        }
    }
    
    private fun isConsecutiveDays(date1: Long, date2: Long): Boolean {
        // date1 and date2 are date keys in format YYYYMMDD
        // Convert back to milliseconds and check if they're exactly 1 day apart
        val diff = kotlin.math.abs(date2 - date1)
        
        // For most consecutive days in same month/year, diff will be exactly 1
        // e.g., 20251209 - 20251208 = 1
        // But we need to handle month/year boundaries too
        // e.g., 20251201 - 20251130 = 71 (not consecutive by subtraction)
        
        // Simple check for same month
        if (diff == 1L) return true
        
        // For month boundaries, check by converting to actual dates
        // This handles cases like Dec 1 and Nov 30
        if (diff in 70L..90L) { // Possible month boundary
            val year1 = (date1 / 10000).toInt()
            val month1 = ((date1 % 10000) / 100).toInt()
            val day1 = (date1 % 100).toInt()
            
            val year2 = (date2 / 10000).toInt()
            val month2 = ((date2 % 10000) / 100).toInt()
            val day2 = (date2 % 100).toInt()
            
            // Check if it's actually consecutive (next day)
            return if (month1 == month2 && year1 == year2) {
                kotlin.math.abs(day2 - day1) == 1
            } else if (year1 == year2) {
                // Different months, check if day2 = 1 and day1 = last day of previous month
                (month2 == month1 + 1 && day2 == 1 && day1 >= 28) ||
                (month1 == month2 + 1 && day1 == 1 && day2 >= 28)
            } else {
                // Different years (Dec 31 -> Jan 1)
                (year2 == year1 + 1 && month2 == 1 && day2 == 1 && month1 == 12 && day1 == 31) ||
                (year1 == year2 + 1 && month1 == 1 && day1 == 1 && month2 == 12 && day2 == 31)
            }
        }
        
        return false
    }

    private fun getMotivationalMessage(days: Int): String = when {
        days > 180 -> "You have plenty of time! Start strong! 💪"
        days in 90..180 -> "Keep up the good work! 📚"
        days in 30..89 -> "Focus mode activated! 🎯"
        days > 0 -> "Final sprint! Give it your all! 🚀"
        else -> "Exam day is here! Best of luck! 🌟"
    }
    
    private fun loadContinueStudying(branchId: Int) {
        viewModelScope.launch {
            
            // Get the last studied subject
            val lastStudiedSubject = repository.getLastStudiedSubject(branchId)
            
            lastStudiedSubject?.let { subject ->
                // Get all chapters for this subject
                repository.getChaptersBySubject(subject.id).collect { chapters ->
                    // Find next incomplete chapter
                    val nextChapter = chapters
                        .filter { !it.isCompleted }
                        .minByOrNull { it.orderIndex }
                    
                    if (nextChapter != null) {
                        _continueStudying.value = ContinueStudyingData(
                            subject = subject,
                            nextChapter = nextChapter
                        )
                    }
                }
            }
        }
    }

}
