
package com.example.ca3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

// 1. Entity
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    val createdAt: Date
)

// 2. TypeConverter
class DateConverter {

    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}

// 3. DAO
@Dao
interface NotesDao {

    @Insert
    suspend fun insertNote(note: NoteEntity)

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Int)
}

// 4. Room Database
@Database(
    entities = [NoteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        fun getDatabase(context: android.content.Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}

// 5. Repository (SSOT)
class NotesRepository(private val dao: NotesDao) {

    val allNotes: Flow<List<NoteEntity>> =
        dao.getAllNotes()

    suspend fun insertNote(note: NoteEntity) {
        dao.insertNote(note)
    }

    suspend fun deleteNote(id: Int) {
        dao.deleteNote(id)
    }
}

// 6. ViewModel
class NotesViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> =
        repository.allNotes.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun addNote(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            repository.insertNote(
                NoteEntity(
                    title = title,
                    content = content,
                    createdAt = Date()
                )
            )
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
}

// 7. ViewModel Factory
class NotesViewModelFactory(
    private val repository: NotesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}

// 8. MainActivity
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = NotesDatabase.getDatabase(applicationContext)
        val repository = NotesRepository(database.notesDao())
        val factory = NotesViewModelFactory(repository)

        setContent {
            val notesViewModel: NotesViewModel =
                viewModel(factory = factory)

            MaterialTheme {
                NotesScreen(notesViewModel)
            }
        }
    }
}

// 9. Compose UI
@Composable
fun NotesScreen(viewModel: NotesViewModel) {

    val notes by viewModel.notes.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "My Notes",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Note Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Note Content") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.addNote(title, content)
                title = ""
                content = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Note")
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Saved Notes",
            style = MaterialTheme.typography.titleLarge
        )

        LazyColumn {
            items(notes, key = { it.id }) { note ->

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            note.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(note.content)

                        Text(
                            SimpleDateFormat(
                                "dd-MM-yyyy HH:mm",
                                Locale.getDefault()
                            ).format(note.createdAt)
                        )

                        TextButton(
                            onClick = {
                                viewModel.deleteNote(note.id)
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
