package com.skillswap.local;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.skillswap.local.activities.AppealActivity;

public class NotificationHelper {
    
    public static void showSuspensionNotification(Context context, String reason) {
        new AlertDialog.Builder(context)
            .setTitle("Account Suspended")
            .setMessage("Your account has been suspended: " + reason + "\n\nYou can submit an appeal to request reinstatement.")
            .setPositiveButton("Submit Appeal", (dialog, which) -> {
                Intent intent = new Intent(context, AppealActivity.class);
                context.startActivity(intent);
            })
            .setNegativeButton("Close", null)
            .setCancelable(false)
            .show();
    }
    
    public static void showAppealStatusNotification(Context context, String status) {
        String message = status.equals("APPROVED") 
            ? "Your appeal has been approved! Your account is now active."
            : "Your appeal was rejected. Your account remains suspended.";
        
        new AlertDialog.Builder(context)
            .setTitle("Appeal Decision")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show();
    }
    
    public static void showWarningNotification(Context context, String title, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
