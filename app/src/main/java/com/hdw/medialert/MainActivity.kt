package com.hdw.medialert

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.hdw.medialert.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hdw.medialert.adapter.ReminderAdapter
import com.hdw.medialert.model.Reminder
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var reminderAdapter: ReminderAdapter
    private val reminders = mutableListOf<Reminder>()
    private val gson = Gson()
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize dark mode
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Make status bar transparent and set light status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }

        setupTopAppBar()
        
        checkNotificationPermission()
        setupRecyclerView()
        loadReminders()

        binding.addReminderFab.setOnClickListener {
            showAddReminderDialog()
        }

        binding.emptyStateButton.setOnClickListener {
            showAddReminderDialog()
        }

        updateEmptyState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupTopAppBar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About MediAlert")
            .setMessage("MediAlert helps you stay on track with your medications by sending timely reminders.\n\nVersion 1.0")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Permission Required")
                        .setMessage("Notification permission is required to show medicine reminders.")
                        .setPositiveButton("OK") { _, _ -> checkNotificationPermission() }
                        .show()
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(reminders) { reminder: Reminder ->
            showEditReminderDialog(reminder)
        }
        binding.remindersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = reminderAdapter
        }
    }

    private fun showAddReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val dosageOptions = arrayOf("1/4", "1/2", "1", "2", "3", "4", "5", "Other")

        // Setup dosage dropdown
        val dosageInput = dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput)
        val adapter = ArrayAdapter(this, R.layout.list_item, dosageOptions)
        dosageInput.setAdapter(adapter)

        // Setup time picker
        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.timeInput)
        timeInput.setOnClickListener {
            showTimePicker { timeString ->
                timeInput.setText(timeString)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Reminder")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                // Save reminder logic
                saveReminder(dialogView)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveReminder(dialogView: View) {
        val medicineName = dialogView.findViewById<TextInputEditText>(R.id.medicineNameInput).text.toString()
        if (medicineName.isEmpty()) {
            dialogView.findViewById<TextInputEditText>(R.id.medicineNameInput).error = "Medicine name is required"
            return
        }
        
        val dosage = dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput).text.toString()
        if (dosage.isEmpty()) {
            dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput).error = "Dosage is required"
            return
        }

        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.timeInput)
        if (timeInput.text.toString().isEmpty()) {
            timeInput.error = "Time is required"
            return
        }
        
        val isDaily = dialogView.findViewById<SwitchMaterial>(R.id.repeatDailySwitch).isChecked
        
        try {
            val reminder = Reminder(
                System.currentTimeMillis(), // unique ID
                medicineName,
                dosage,
                calendar.timeInMillis,
                isDaily
            )
            
            reminders.add(reminder)
            reminderAdapter.notifyItemInserted(reminders.size - 1)
            saveReminders()
            scheduleReminder(reminder)
            updateEmptyState()
        } catch (e: Exception) {
            // Show error message to user
            MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage("Failed to create reminder. Please try again.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun scheduleReminder(reminder: Reminder) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ReminderReceiver::class.java).apply {
                putExtra("REMINDER_ID", reminder.id)
                putExtra("MEDICINE_NAME", reminder.medicineName)
                putExtra("DOSAGE", reminder.dosage)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (reminder.isDaily) {
                val reminderTime = Calendar.getInstance().apply {
                    timeInMillis = reminder.time
                }
                val currentTime = Calendar.getInstance()
                
                if (currentTime.after(reminderTime)) {
                    reminderTime.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            reminderTime.timeInMillis,
                            AlarmManager.INTERVAL_DAY,
                            pendingIntent
                        )
                    } else {
                        // Request permission to schedule exact alarms
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivity(intent)
                    }
                } else {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminder.time,
                            pendingIntent
                        )
                    } else {
                        // Request permission to schedule exact alarms
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivity(intent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.time,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage("Failed to schedule reminder. Please try again.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveReminders() {
        val sharedPrefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        val json = gson.toJson(reminders)
        sharedPrefs.edit().putString("reminders_list", json).apply()
    }

    private fun loadReminders() {
        val sharedPrefs = getSharedPreferences("Reminders", Context.MODE_PRIVATE)
        val json = sharedPrefs.getString("reminders_list", null)
        if (json != null) {
            val type = object : TypeToken<List<Reminder>>() {}.type
            val loadedReminders = gson.fromJson<List<Reminder>>(json, type)
            reminders.clear()
            reminders.addAll(loadedReminders)
            reminderAdapter.notifyDataSetChanged()
            
            // Reschedule all daily reminders
            reminders.filter { it.isDaily }.forEach { scheduleReminder(it) }
        }
        updateEmptyState()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val currentTime = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                try {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    
                    // If selected time is in the past, add one day
                    if (calendar.timeInMillis < System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val timeString = timeFormat.format(calendar.time)
                    onTimeSelected(timeString)
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Error")
                        .setMessage("Failed to set time. Please try again.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            },
            currentTime.get(Calendar.HOUR_OF_DAY),
            currentTime.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun showEditReminderDialog(reminder: Reminder) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val dosageOptions = arrayOf("1/4", "1/2", "1", "2", "3", "4", "5", "Other")

        // Setup existing values
        dialogView.findViewById<TextInputEditText>(R.id.medicineNameInput).setText(reminder.medicineName)
        dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput).setText(reminder.dosage)
        dialogView.findViewById<TextInputEditText>(R.id.timeInput).setText(
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(reminder.time))
        )
        dialogView.findViewById<SwitchMaterial>(R.id.repeatDailySwitch).isChecked = reminder.isDaily

        // Setup dosage dropdown
        val dosageInput = dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput)
        val adapter = ArrayAdapter(this, R.layout.list_item, dosageOptions)
        dosageInput.setAdapter(adapter)

        // Setup time picker
        val timeInput = dialogView.findViewById<TextInputEditText>(R.id.timeInput)
        timeInput.setOnClickListener {
            showTimePicker { timeString ->
                timeInput.setText(timeString)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Reminder")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                updateReminder(reminder, dialogView)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ ->
                deleteReminder(reminder)
            }
            .show()
    }

    private fun updateReminder(oldReminder: Reminder, dialogView: View) {
        val medicineName = dialogView.findViewById<TextInputEditText>(R.id.medicineNameInput).text.toString()
        val dosage = dialogView.findViewById<AutoCompleteTextView>(R.id.dosageInput).text.toString()
        val isDaily = dialogView.findViewById<SwitchMaterial>(R.id.repeatDailySwitch).isChecked

        val updatedReminder = Reminder(
            oldReminder.id,
            medicineName,
            dosage,
            calendar.timeInMillis,
            isDaily
        )

        val index = reminders.indexOfFirst { it.id == oldReminder.id }
        if (index != -1) {
            reminders[index] = updatedReminder
            reminderAdapter.notifyItemChanged(index)
            saveReminders()
            scheduleReminder(updatedReminder)
        }
    }

    private fun deleteReminder(reminder: Reminder) {
        val index = reminders.indexOfFirst { it.id == reminder.id }
        if (index != -1) {
            reminders.removeAt(index)
            reminderAdapter.notifyItemRemoved(index)
            saveReminders()
            cancelReminder(reminder)
        }
    }

    private fun cancelReminder(reminder: Reminder) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateEmptyState() {
        if (reminders.isEmpty()) {
            binding.remindersRecyclerView.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.VISIBLE
        } else {
            binding.remindersRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
        }
    }
}