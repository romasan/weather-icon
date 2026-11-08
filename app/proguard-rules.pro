# WorkManager creates this worker by class name from its persisted work request.
-keep class ru.weathericon.app.RefreshWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
