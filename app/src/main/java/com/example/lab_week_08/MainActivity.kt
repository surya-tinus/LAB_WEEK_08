package com.example.lab_week_08

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.lab_week_08.worker.FirstWorker
import com.example.lab_week_08.worker.SecondWorker
// BARU: Import worker dan service yang baru
import com.example.lab_week_08.worker.ThirdWorker

class MainActivity : AppCompatActivity() {

    private val workManager = WorkManager.getInstance(this)

    // BARU: Definisikan network constraints di sini agar bisa dipakai ulang
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v,
                                                                             insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                systemBars.bottom)
            insets
        }

        // Pindahkan requestPermission ke atas agar lebih rapi
        checkNotificationPermission()

        // Jalankan rantai WorkManager
        startWorkChain()
    }

    private fun startWorkChain() {
        val id = "001"

        val firstRequest = OneTimeWorkRequest
            .Builder(FirstWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(FirstWorker
                .INPUT_DATA_ID, id)
            ).build()

        val secondRequest = OneTimeWorkRequest
            .Builder(SecondWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(SecondWorker
                .INPUT_DATA_ID, id)
            ).build()

        workManager.beginWith(firstRequest)
            .then(secondRequest)
            .enqueue()

        // Observer untuk FirstWorker
        workManager.getWorkInfoByIdLiveData(firstRequest.id)
            .observe(this) { info ->
                info?.let {
                    if (it.state == WorkInfo.State.SUCCEEDED) { // Lebih baik cek SUCCEEDED
                        showResult("First process is done")
                    }
                }
            }

        // Observer untuk SecondWorker (Pemicu NotificationService)
        workManager.getWorkInfoByIdLiveData(secondRequest.id)
            .observe(this) { info ->
                info?.let {
                    if (it.state == WorkInfo.State.SUCCEEDED) { // Lebih baik cek SUCCEEDED
                        showResult("Second process is done")
                        // Langkah 3: Jalankan Notif Service Pertama
                        launchNotificationService()
                    }
                }
            }
    }

    //Build the data into the correct format before passing it to the worker as input
    private fun getIdInputData(idKey: String, idValue: String) =
        Data.Builder()
            .putString(idKey, idValue)
            .build()

    //Show the result as toast
    private fun showResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    //Launch the NotificationService
    private fun launchNotificationService() {
        //Observe if the service process is done or not
        //If it is, show a toast with the channel ID in it
        NotificationService.trackingCompletion.observe(
            this) { id ->
            // Pastikan observer ini hanya berjalan sekali
            NotificationService.trackingCompletion.removeObservers(this)

            showResult("Process for Notification Channel ID $id is done!")

            // BARU: Langkah 4 -> Jalankan ThirdWorker
            launchThirdWorker()
        }

        val serviceIntent = Intent(
            this,
            NotificationService::class.java
        ).apply {
            putExtra(EXTRA_ID, "001")
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // BARU: Fungsi untuk menjalankan ThirdWorker
    private fun launchThirdWorker() {
        val id = "002" // Kita gunakan ID baru

        val thirdRequest = OneTimeWorkRequest
            .Builder(ThirdWorker::class.java)
            .setConstraints(networkConstraints) // Pakai constraints yang sama
            .setInputData(getIdInputData(ThirdWorker.INPUT_DATA_ID, id))
            .build()

        workManager.enqueue(thirdRequest)

        // BARU: Observer untuk ThirdWorker (Pemicu SecondNotificationService)
        workManager.getWorkInfoByIdLiveData(thirdRequest.id)
            .observe(this) { info ->
                info?.let {
                    if (it.state == WorkInfo.State.SUCCEEDED) {
                        // Pastikan observer ini hanya berjalan sekali
                        workManager.getWorkInfoByIdLiveData(thirdRequest.id).removeObservers(this)

                        showResult("Third process is done")

                        // BARU: Langkah 5 -> Jalankan Notif Service Kedua
                        launchSecondNotificationService()
                    }
                }
            }
    }

    // BARU: Fungsi untuk menjalankan SecondNotificationService
    private fun launchSecondNotificationService() {
        // Observe service kedua
        SecondNotificationService.trackingCompletion.observe(this) { id ->
            SecondNotificationService.trackingCompletion.removeObservers(this)
            showResult("Process for Second Notification Channel ID $id is done!")
        }

        // Buat Intent untuk service kedua
        val serviceIntent = Intent(
            this,
            SecondNotificationService::class.java
        ).apply {
            // Gunakan Key ID yang baru dari companion object service kedua
            putExtra(SecondNotificationService.EXTRA_ID, "002")
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // BARU: Fungsi untuk cek izin notifikasi
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {

                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }

    companion object{
        // Tetap gunakan ini untuk Service pertama
        const val EXTRA_ID = "Id"

        // (Tidak perlu EXTRA_ID2 di sini karena kita mengambilnya
        // langsung dari companion object SecondNotificationService)
    }
}