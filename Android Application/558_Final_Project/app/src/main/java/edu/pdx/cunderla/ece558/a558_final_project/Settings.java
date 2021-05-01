package edu.pdx.cunderla.ece558.a558_final_project;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * The settings activity serves two purposes:
 * 1. give users the opportunity to change settings on their device as well as for the thermostat system
 * 2. provide information about the application
 *
 * The settings activity allows the user to turn off the text to speech functionality for thier
 * device only using shared preferences as well as allows users to change the location of the
 * thermostat which is then updated on the Firebase to be used by all mobile devices running the
 * thermostat application.
 */

public class Settings extends AppCompatActivity {

    private Button TTSbutton, LocationButton;
    private SharedPreferences shared_preferences;
    private SharedPreferences.Editor editor;
    private DatabaseReference mDatabase;

    private boolean tts_on_off = true;
    private String city = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        LocationButton = findViewById(R.id.location_ET);
        TTSbutton = findViewById(R.id.tts_on_off);
        mDatabase = FirebaseDatabase.getInstance().getReference();

        shared_preferences = getApplicationContext().getSharedPreferences("MyPref", 0);

        // retrieve setting for text-to-speech
        if(shared_preferences.contains("tts_state")){
            tts_on_off = shared_preferences.getBoolean("tts_state", true);
        }

        // set the button corresponding to text to speech
        set_tts_button(tts_on_off);

        // initializing and linking listener for text to speech on/off button
        TTSbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // invert the current state of the button
                tts_on_off = !tts_on_off;
                // set the button to new value
                set_tts_button(tts_on_off);
                // access shared preferences
                shared_preferences = getApplicationContext().getSharedPreferences("MyPref", 0);
                // create editor for shared preferences
                editor = shared_preferences.edit();
                // write the button state to shared preferences
                editor.putBoolean("tts_state",tts_on_off);
                // commit changes to shared preferences
                editor.commit();
            }
        });

        LocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // lock the device in the orientation it is currently in
                lockDeviceRotation(true);
                // create a new AlertDialog
                AlertDialog.Builder builder = new AlertDialog.Builder(Settings.this);
                // set the preferences for the dialog
                builder.setTitle("Location Change Requested!");
                builder.setMessage("Please enter your city.");
                builder.setIcon(R.mipmap.ic_launcher_home_round);
                final EditText input = new EditText(Settings.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);
                // prompt the user to enter a new location for the thermostat
                // when the user presses ok...
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // update Firebase with the value the user provided
                        mDatabase.child("current_location").setValue(input.getText().toString());
                        // allow orientation changes
                        lockDeviceRotation(false);
                    }
                });
                // when the user presses cancel...
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // allow orientation changes
                        lockDeviceRotation(false);
                        dialog.cancel();
                    }
                });

                builder.show();
            }

        });

        // initializing and linking the listener for the "current_location" child in the Firebasee
        mDatabase.child("current_location").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // get the value of the current location
                city = dataSnapshot.getValue(String.class);
                // set the location button text to the current location
                LocationButton.setText(city);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    /**
     * Simple method that changes the background of a button indicating whether the text to speech functionality is on or off
     *
     * @param on_off boolean value that dictates which background is shown for the on/off button for text to speech
     */

    private void set_tts_button(boolean on_off){
        // if button on...
        if(on_off){
            // set button background to on button drawable
            TTSbutton.setBackgroundDrawable(getResources().getDrawable(R.drawable.switch_on));
        }
        // if button off...
        else{
            // set button background to off button drawable
            TTSbutton.setBackgroundDrawable(getResources().getDrawable(R.drawable.switch_off));
        }
    }

    /**
     * The group did not create this method.  The original source of this mthod can be found
     * here: https://riptutorial.com/android/example/21077/lock-screen-s-rotation-programmatically.
     * This method allows for the device's orientation to be locked dynamically.  This method is
     * used to lock the orientation of the screen when a dialog box is displayed to avoid the
     * app from crashing.
     *
     * @param value boolean value that dictates if the orientation is locked (true) or not (false)
     */

    public void lockDeviceRotation(boolean value) {
        if (value) {
            int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            }
        }
    }

}
