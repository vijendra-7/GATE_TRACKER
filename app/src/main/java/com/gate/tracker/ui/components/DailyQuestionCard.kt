package com.gate.tracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Daily Question Card - displays a daily question for user engagement
 * Data will be fetched from remote config
 */
@Composable
fun DailyQuestionCard(
    question: String = "What is the time complexity of QuickSort in the worst case?",
    options: List<String> = listOf("O(n)", "O(n log n)", "O(n²)", "O(log n)"),
    correctAnswer: Int = 2, // Index of correct answer (0-based)
    explanation: String = "QuickSort has O(n²) worst-case complexity when the pivot selection is poor (e.g., sorted array with first element as pivot).",
    onAnswerSubmit: ((Int) -> Unit)? = null
) {
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var showExplanation by remember { mutableStateOf(false) }
    
    // Shimmer animation for the card
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Daily Question",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Challenge",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            
            // Question
            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // Options
            options.forEachIndexed { index, option ->
                val isSelected = selectedAnswer == index
                val isCorrect = index == correctAnswer
                val showResult = showExplanation
                
                val backgroundColor = when {
                    showResult && isCorrect -> Color(0xFF22C55E).copy(alpha = 0.2f)
                    showResult && isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.2f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }
                
                val borderColor = when {
                    showResult && isCorrect -> Color(0xFF22C55E)
                    showResult && isSelected && !isCorrect -> Color(0xFFEF4444)
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !showExplanation) {
                            selectedAnswer = index
                        },
                    color = backgroundColor,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected || (showResult && isCorrect)) 2.dp else 1.dp,
                        color = borderColor
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (showResult && isCorrect) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            // Submit/Next Button
            Button(
                onClick = {
                    if (!showExplanation && selectedAnswer != null) {
                        showExplanation = true
                        onAnswerSubmit?.invoke(selectedAnswer!!)
                    } else {
                        // Reset for next question
                        selectedAnswer = null
                        showExplanation = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedAnswer != null || showExplanation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showExplanation) 
                        MaterialTheme.colorScheme.secondary 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (showExplanation) "Next Question" else "Submit Answer",
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Explanation (shown after submission)
            if (showExplanation) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selectedAnswer == correctAnswer) 
                        Color(0xFF22C55E).copy(alpha = 0.1f)
                    else 
                        Color(0xFFFBBF24).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (selectedAnswer == correctAnswer) "✓ Correct!" else "✗ Incorrect",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedAnswer == correctAnswer) 
                                Color(0xFF22C55E) 
                            else 
                                Color(0xFFEF4444)
                        )
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
