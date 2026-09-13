package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.MediaRepository
import com.example.ui.MovieAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MovieViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MediaRepository(database.watchlistDao())
        val viewModelFactory = MovieViewModel.Factory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MovieViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MovieAppScreen(viewModel = viewModel)
            }
        }
    }
}
