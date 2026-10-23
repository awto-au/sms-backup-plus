package sms.backup.plus.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import sms.backup.plus.App;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.preferences.AuthPreferences;
import sms.backup.plus.preferences.Preferences;
import sms.backup.plus.service.BackupType;
import sms.backup.plus.service.CancelEvent;
import sms.backup.plus.service.SmsBackupService;
import sms.backup.plus.service.SmsRestoreService;

import static sms.backup.plus.App.TAG;
import static sms.backup.plus.compat.SmsReceiver.isSmsBackupDefaultSmsApp;
import static sms.backup.plus.service.BackupType.MANUAL;
import static sms.backup.plus.service.BackupType.SKIP;

/**
 * Debug-only entry point for the main-screen buttons, so they can be driven from adb:
 * <pre>
 * adb shell am start -n sms.backup.plus/.debug.DebugActionActivity --es action backup
 * adb logcat -s SMSBackup+
 * </pre>
 * Actions: backup, backup-skip, cancel-backup, restore, cancel-restore, status.
 * Skips the confirm and first-sync dialogs that the buttons show.
 */
public class DebugActionActivity extends Activity {
    static final String EXTRA_ACTION = "action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String action = getIntent().getStringExtra(EXTRA_ACTION);
        Log.i(TAG, "debug action: " + action);
        try {
            dispatch(action);
        } finally {
            finish();
        }
    }

    private void dispatch(String action) {
        if (action == null) {
            Log.e(TAG, "debug action: missing --es " + EXTRA_ACTION);
            return;
        }
        switch (action) {
            case "backup":
                startBackup(MANUAL);
                break;
            case "backup-skip":
                startBackup(SKIP);
                break;
            case "cancel-backup":
            case "cancel-restore":
                Log.i(TAG, "debug action: posting CancelEvent (backupRunning="
                        + SmsBackupService.isServiceWorking() + ", restoreIdle=" + SmsRestoreService.isServiceIdle() + ")");
                App.post(new CancelEvent());
                break;
            case "restore":
                startRestore();
                break;
            case "status":
                logStatus();
                break;
            default:
                Log.e(TAG, "debug action: unknown '" + action + "'");
        }
    }

    private void startBackup(BackupType type) {
        if (!new AuthPreferences(this).isLoginInformationSet()) {
            Log.e(TAG, "backup refused: no login information set");
            return;
        }
        if (SmsBackupService.isServiceWorking()) {
            Log.w(TAG, "backup refused: already running");
            return;
        }
        Log.i(TAG, "starting backup " + type);
        startService(new Intent(this, SmsBackupService.class).setAction(type.name()));
    }

    private void startRestore() {
        if (!SmsRestoreService.isServiceIdle()) {
            Log.w(TAG, "restore refused: already running");
            return;
        }
        if (!isSmsBackupDefaultSmsApp(this)) {
            // MainActivity swaps the default SMS app first; this entry point does not
            Log.e(TAG, "restore refused: not the default SMS app, start a restore from the UI once");
            return;
        }
        Log.i(TAG, "starting restore");
        startService(new Intent(this, SmsRestoreService.class));
    }

    private void logStatus() {
        final Preferences preferences = new Preferences(this);
        Log.i(TAG, "status: loginSet=" + new AuthPreferences(this).isLoginInformationSet()
                + " backupRunning=" + SmsBackupService.isServiceWorking()
                + " restoreIdle=" + SmsRestoreService.isServiceIdle()
                + " defaultSmsApp=" + isSmsBackupDefaultSmsApp(this));
        for (DataType dataType : preferences.getDataTypePreferences().enabled()) {
            Log.i(TAG, "status: enabled " + dataType + " maxSynced=" + preferences.getDataTypePreferences().getMaxSyncedDate(dataType));
        }
        Log.i(TAG, "status: mostRecentSynced=" + preferences.getDataTypePreferences().getMostRecentSyncedDate());
    }
}
