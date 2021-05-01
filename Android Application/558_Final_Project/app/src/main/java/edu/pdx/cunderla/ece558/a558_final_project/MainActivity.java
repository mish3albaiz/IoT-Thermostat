package edu.pdx.cunderla.ece558.a558_final_project;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.androdocs.httprequest.HttpRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.xw.repo.BubbleSeekBar;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import io.feeeei.circleseekbar.CircleSeekBar;

/**
 * The main activity serves as the control panel as well as main dashboard for the thermostat system.
 * Within this activity the user can adjust the thermostat setpoint as well as the fan mode.  The
 * activity also provides insight into current thermostat settings and parameters such as current
 * temperature reading from Pi, current outside temp using OpenWeather API, current setpoint and
 * current fan mode and temperature zone.  The activity also provides buttons to open other
 * application activities as well as provides a button to allow the user to use speech to text
 * to change system settings
 *
 */

public class MainActivity extends AppCompatActivity {

    private int set_temp_value = 0;
    private int old_fan_zone = -1;
    private int temp_in = 0;
    private int set_temp = 0;
    private final int REQ_CODE = 100;
    private String fan_mode = "";
    private String my_user_name;
    private String API = "XXXX";
    private String city = "Portland";
    private String old_fan;
    private String global_fan_mode = "";
    private boolean tts_on_off = true;
    private boolean first_bootup = true;
    private boolean write_fan_to_FB = true;

    private Button setButton;
    private DatabaseReference mDatabase;
    private CircleSeekBar mTempSeekBar;
    private TextView mTempText, mSetText, mOutsideTemp, mFanMode;
    private Vibrator vibe;
    private ImageButton history_button, graph_button, settings_button, speech_button;
    private ProgressBar currentTempPB, setTempPB, localTempPB;
    private TextToSpeech tts;
    private Animation rotateFan;
    private ImageView fanIV;
    private BubbleSeekBar bubbleSeekBar4;
    private SharedPreferences shared_preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        setButton = findViewById(R.id.set_button);
        mTempSeekBar = findViewById(R.id.seekbar);
        mTempText = findViewById(R.id.temp_text);
        mOutsideTemp = findViewById(R.id.local_temp_TV);
        mSetText = findViewById(R.id.thermo_text);
        history_button = findViewById(R.id.history);
        graph_button = findViewById(R.id.graph);
        currentTempPB = findViewById(R.id.temp_progress_bar);
        setTempPB = findViewById(R.id.set_temp_PB);
        speech_button = findViewById(R.id.speech);
        localTempPB = findViewById(R.id.local_temp_PB);
        fanIV = findViewById(R.id.fan);
        mFanMode = findViewById(R.id.fan_mode_TV);
        settings_button = findViewById(R.id.settings);
        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        bubbleSeekBar4 = findViewById(R.id.demo_4_seek_bar_4);

        setButton.setText("ADJUST TO\nSET TEMP");

        // setting custom text labels for the custom Bubble SeekBar package
        bubbleSeekBar4.setCustomSectionTextArray(new BubbleSeekBar.CustomSectionTextArray() {
            @NonNull
            @Override
            public SparseArray<String> onCustomize(int sectionCount, @NonNull SparseArray<String> array) {
                array.clear();
                // from left to right set labels, off, auto, low, medium and high
                array.put(0, "OFF");
                array.put(1, "AUTO");
                array.put(2, "LOW");
                array.put(3, "MED");
                array.put(4, "HIGH");

                return array;
            }
        });

        // initializing and linking listener to bubble seekbar (fan mode seekbar)
        bubbleSeekBar4.setOnProgressChangedListener(new BubbleSeekBar.OnProgressChangedListener() {
            boolean onActionUsed = false;

            @Override
            public void onProgressChanged(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
            }

            @Override
            public void getProgressOnActionUp(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat) {
                // determine which fan mode is selected by the seekbar, return string value indicating the fan mode
                switch(progress){
                    case 0:
                        fan_mode = "off";
                        break;
                    case 25:
                        fan_mode = "automatic";
                        break;
                    case 50:
                        fan_mode = "low";
                        break;
                    case 75:
                        fan_mode = "medium";
                        break;
                    case 100:
                        fan_mode = "high";
                        break;
                }

                // if the fan mode is different from the last fan mode
                if(fan_mode != global_fan_mode){
                    // if the current device changed the setting and not a different device
                    if(write_fan_to_FB) {
                        // write the fan mode to the Firebase
                        mDatabase.child("fan_mode").setValue(fan_mode);
                        // if the power is off generate a custom update message indicating the power is off
                        if (progress == 0) {
                            update_history("power_off");
                        // if the fan mode has changed generate a custom message indicating the fan mode has changed
                        } else {
                            update_history("set_fan");
                        }
                    }

                    // if the fan mode is automatic and text to speech is on then make verbal indication that the fan mode has been changed to automatic
                    if (fan_mode == "automatic" && tts_on_off) {
                        tts.speak("Fan mode set to automatic", TextToSpeech.QUEUE_FLUSH, null);
                    // else if the fan mode is not off and text to speech is on then make a verbal indication that the system is operating in a manual fan mode
                    } else if (tts_on_off && fan_mode != "off") {
                        tts.speak("Fan mode set to manual at " + fan_mode + "speed", TextToSpeech.QUEUE_FLUSH, null);
                    // else if text to speech is on then make a verbal indication that the system is off
                    } else if (tts_on_off) {
                        tts.speak("Thermostat turned off", TextToSpeech.QUEUE_FLUSH, null);
                    }
                    // save the fan mode for comparision later
                    global_fan_mode = fan_mode;
                }
                // if the fan mode did not change then vibrate the device and indicate that the fan mode did not change
                else{
                    vibe.vibrate(100);
                    Toast.makeText(MainActivity.this, "Thermostat is already set to that setting.", Toast.LENGTH_SHORT).show();
                }
                // indicate that the getProgressOnActionUp() method was already used and not to execute the getProgressOnFinally() method
                onActionUsed = true;
            }

            @Override
            public void getProgressOnFinally(BubbleSeekBar bubbleSeekBar, int progress, float progressFloat, boolean fromUser) {
                // if the application isn't booting up for the first time
                if(!first_bootup) {
                    // determine which fan mode is selected by the seekbar, return string value indicating the fan mode
                    switch (progress) {
                        case 0:
                            fan_mode = "off";
                            break;
                        case 25:
                            fan_mode = "automatic";
                            break;
                        case 50:
                            fan_mode = "low";
                            break;
                        case 75:
                            fan_mode = "medium";
                            break;
                        case 100:
                            fan_mode = "high";
                            break;
                    }

                    // if the fan mode is different from the last fan mode
                    if(fan_mode != global_fan_mode) {
                        // if the current device changed the setting and not a different device
                        if(write_fan_to_FB) {
                            // write the fan mode to the Firebase
                            mDatabase.child("fan_mode").setValue(fan_mode);
                            // if the power is off generate a custom update message indicating the power is off
                            if (progress == 0) {
                                update_history("power_off");
                            }
                            // if the fan mode has changed generate a custom message indicating the fan mode has changed
                            else {
                                update_history("set_fan");
                            }
                        }

                        // if the fan mode is automatic and text to speech is on then make verbal indication that the fan mode has been changed to automatic
                        if (fan_mode == "automatic" && tts_on_off) {
                            tts.speak("Fan mode set to automatic", TextToSpeech.QUEUE_FLUSH, null);
                        // else if the fan mode is not off and text to speech is on then make a verbal indication that the system is operating in a manual fan mode
                        } else if (tts_on_off && fan_mode != "off") {
                            tts.speak("Fan mode set to manual at " + fan_mode + "speed", TextToSpeech.QUEUE_FLUSH, null);
                        // else if text to speech is on then make a verbal indication that the system is off
                        } else if (tts_on_off) {
                            tts.speak("Thermostat turned off", TextToSpeech.QUEUE_FLUSH, null);
                        }
                        // save the fan mode for comparision later
                        global_fan_mode = fan_mode;
                    }
                    // if the fan mode did not change then vibrate the device and indicate that the fan mode did not change
                    else if(!onActionUsed){
                        vibe.vibrate(100);
                        Toast.makeText(MainActivity.this, "Thermostat is already set to that setting.", Toast.LENGTH_SHORT).show();
                        onActionUsed = false;
                    }
                }
                // if first bootup get the current fan mode
                else{
                    switch (progress) {
                        case 0:
                            fan_mode = "off";
                            break;
                        case 25:
                            fan_mode = "automatic";
                            break;
                        case 50:
                            fan_mode = "low";
                            break;
                        case 75:
                            fan_mode = "medium";
                            break;
                        case 100:
                            fan_mode = "high";
                            break;
                    }
                    first_bootup = false;
                    global_fan_mode = fan_mode;
                }
            }
        });

        // linking and initializing seekbar to adjust thermostat temperature
        mTempSeekBar.setOnSeekBarChangeListener(new CircleSeekBar.OnSeekBarChangeListener() {

            @Override
            public void onChanged(CircleSeekBar seekbar, int curValue) {

                // set the color of the seekbar depending on the temperature value
                mTempSeekBar.setReachedColor(Color.parseColor(set_color(curValue)));
                // obtain the current setpoint value
                set_temp_value = curValue;
                // change the text on the setpoint button to read the current temperature value of the seekbar
                setButton.setText("Press to\nSet to\n" + String.valueOf(curValue)+"\u00B0C");
            }

        });

        // linking and initializing button to set the setpoint value of the system
        setButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // if the setpoint is different then the current setpoint and the system is on
                if(set_temp_value != set_temp && bubbleSeekBar4.getProgress() != 0) {
                    // write the temperature value to the Firebase
                    mDatabase.child("set_temp").setValue(set_temp_value);
                    // vibrate the device
                    vibe.vibrate(150);
                    // create a custom update message that indicates who made the change and when the setpoint was changed as well as what the setpoint was set to
                    update_history("set_temp");
                    // if text to speech is on then make a verbal indicatino that the setpoint was changed
                    if(tts_on_off){
                        tts.speak("Setpoint set to " + String.valueOf(set_temp_value) + "\u00B0C", TextToSpeech.QUEUE_FLUSH, null);

                    }
                }
                // if the system is off...
                else if(bubbleSeekBar4.getProgress() == 0){
                    // vibrate the device
                    vibe.vibrate(100);
                    // make a toast indicating that the system must be on to change the setpoint
                    Toast.makeText(MainActivity.this, "Turn power on to set thermostat.", Toast.LENGTH_SHORT).show();
                }
                else{
                    // vibrate the device
                    vibe.vibrate(100);
                    // make a toast indicating that the setpoint is already at that temperature value
                    Toast.makeText(MainActivity.this, "Temperature is already set to " + String.valueOf(set_temp_value) + "\u00B0C", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // linking and initializing button to open up the history activity
        history_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send an intent to start the history activity
                Intent myIntent = new Intent(MainActivity.this, Updates.class);
                startActivity(myIntent);
            }
        });

        // linking and initializing button to open the settings activity
        settings_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send an intent to start the settings activity
                Intent myIntent = new Intent(MainActivity.this, Settings.class);
                startActivity(myIntent);
            }
        });

        // linking and initializing button to open graph activity
        graph_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send an intent to start the graph activity
                Intent myIntent = new Intent(MainActivity.this, Graph.class);
                myIntent.putExtra("set_point", set_temp);
                myIntent.putExtra("temp", temp_in);
                startActivity(myIntent);
            }
        });

        // linking and initializing value listener for the "current_location" child of the Firebase
        mDatabase.child("current_location").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // save the value of the child
                city = dataSnapshot.getValue(String.class);
                // execute the weatherTask AsyncTask to get the current weather for the specified location
                new weatherTask().execute();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // linking and initializing value listener for the "fan_mode" child of the Firebase
        mDatabase.child("fan_mode").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // retrieve the value of the child
                String fan_mode_setting = dataSnapshot.getValue(String.class);
                // check to see if the fan mode has changed
                if(fan_mode_setting != old_fan){
                    // animate the fan ImageView using the new fan mode setting
                    animate_fan(fan_mode_setting);
                    // save the fan setting to compare later
                    old_fan = fan_mode_setting;
                }

                // determine what temperature zone the system is in and adjust fan ImageView color
                old_fan_zone = set_fan_color(temp_in,set_temp,old_fan_zone);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // linking and initializing value listener for the "current_temp" child of the Firebase
        mDatabase.child("current_temp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // read value from Firebase
                temp_in = dataSnapshot.getValue(Integer.class);
                // set TextView widget with temperature value
                mTempText.setText(String.valueOf(temp_in)+"\u00B0C");
                // set TextView color based on temperature region
                mTempText.setTextColor(Color.parseColor(set_color(temp_in)));
                // set progressbar with new temperature value
                currentTempPB.setProgress(temp_in);
                // depending on temperature, change the color the progressbar to blue, green, orange or red
                if(temp_in > 35){
                    currentTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_red), PorterDuff.Mode.SRC_IN);
                }
                else if(20 < temp_in && temp_in <= 25 ){
                    currentTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_green), PorterDuff.Mode.SRC_IN);
                }
                else if(25 < temp_in && temp_in <= 35){
                    currentTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_orange), PorterDuff.Mode.SRC_IN);
                }
                else{
                    currentTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_blue), PorterDuff.Mode.SRC_IN);
                }

                // determine what temperature zone the system is in and adjust fan ImageView color
                old_fan_zone = set_fan_color(temp_in,set_temp,old_fan_zone);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // linking and initializing value listener for the "set_temp" child of the Firebase
        mDatabase.child("set_temp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // read child value from Firebase
                set_temp = dataSnapshot.getValue(Integer.class);
                // set TextView text with new setpoint value
                mSetText.setText(String.valueOf(set_temp)+"\u00B0C");
                // set TextView color depending on temerature zone
                mSetText.setTextColor(Color.parseColor(set_color(set_temp)));
                // set progressbar using new setpoint value
                setTempPB.setProgress(set_temp);
                // depending on temperature, change the color the progressbar to blue, green, orange or red
                if(set_temp > 35){
                    setTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_red), PorterDuff.Mode.SRC_IN);
                }
                else if(20 < set_temp && set_temp <= 25 ){
                    setTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_green), PorterDuff.Mode.SRC_IN);
                }
                else if(25 < set_temp && set_temp <= 35){
                    setTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_orange), PorterDuff.Mode.SRC_IN);
                }
                else{
                    setTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_blue), PorterDuff.Mode.SRC_IN);
                }

                // determine what temperature zone the system is in and adjust fan ImageView color
                old_fan_zone = set_fan_color(temp_in,set_temp,old_fan_zone);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // linking and initializing value listener for the "users" child of the Firebase
        mDatabase.child("users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        // new array list to hold all the current users of the application
                        List<String> phone_id_array = new ArrayList<>();

                        // iterate through all the users stored in the Firebase
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                            String phone_id = snapshot.getKey();
                            phone_id_array.add(phone_id);
                        }

                        // check to see if the current device is registered in the Firebase by using it's serial number
                        if(phone_id_array.contains(String.valueOf(Build.SERIAL))){
                            // get users name using their serial number
                            my_user_name = dataSnapshot.child(String.valueOf(Build.SERIAL)).getValue(String.class);
                        }
                        // if serial number not in Firebase
                        else {
                            // create AlertDialog window asking user to provide their desired username
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("New Device Detected!");
                            builder.setMessage("Please enter your name.");
                            builder.setIcon(R.mipmap.ic_launcher_home_round);
                            final EditText input = new EditText(MainActivity.this);
                            input.setInputType(InputType.TYPE_CLASS_TEXT);
                            builder.setView(input);
                            builder.setCancelable(false);
                            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // save the name the user provided to the Firebase
                                    mDatabase.child("users").child(String.valueOf(Build.SERIAL)).setValue(input.getText().toString());
                                    // save the name as instance variable to be used in update messages
                                    my_user_name = input.getText().toString();
                                    // create an update message saying that a new user has been added
                                    mDatabase.child("updates").child(String.valueOf(Calendar.getInstance().getTime())).setValue(my_user_name + " added as new member.");
                                }
                            });
                            builder.show();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

        // creating new text to speech object
        tts=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            // initializing text to speech object with language, pitch and speed
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                    tts.setPitch(1);
                    tts.setSpeechRate(1);
                }
            }
        });

        // linking and initializing button listener to use voice commands
        speech_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // code used from https://riptutorial.com/android/example/21077/lock-screen-s-rotation-programmatically
                // starts new activity to capture user's voice and return string to onActivityResult()
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like to do?");
                try {
                    startActivityForResult(intent, REQ_CODE);
                } catch (ActivityNotFoundException a) {
                    Toast.makeText(getApplicationContext(),
                            "Sorry your device not supported",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        ArrayList result = null;

        try{

            // attempt to obtain string that contains what the user spoke
            switch (requestCode) {
                case REQ_CODE: {
                    if (resultCode == RESULT_OK && null != data) {
                        result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    }
                    break;
                }
            }

            // check to see if the user wants to change fan mode using several keywords
            if(String.valueOf(result.get(0)).contains("power") || String.valueOf(result.get(0)).contains("turn") || String.valueOf(result.get(0)).contains("fan")){

                // if user says keywords for automatic fan mode
                if(String.valueOf(result.get(0)).contains("automatic") || String.valueOf(result.get(0)).contains("auto")){
                    // set fan mode seekbar
                    bubbleSeekBar4.setProgress(25);
                    // update global fan mode
                    fan_mode = "automatic";
                    // update Firebase
                    mDatabase.child("fan_mode").setValue(fan_mode);
                    // create update message
                    update_history("set_fan");
                }
                // if user says keywords for manual high fan mode
                else if(String.valueOf(result.get(0)).contains("high") || String.valueOf(result.get(0)).contains("hi")){
                    // set fan mode seekbar
                    bubbleSeekBar4.setProgress(100);
                    // update global fan mode
                    fan_mode = "high";
                    // update Firebase
                    mDatabase.child("fan_mode").setValue(fan_mode);
                    // create update message
                    update_history("set_fan");
                }
                // if user says keywords for manual medium fan mode
                else if(String.valueOf(result.get(0)).contains("medium") || String.valueOf(result.get(0)).contains("med")){
                    // set fan mode seekbar
                    bubbleSeekBar4.setProgress(75);
                    // update global fan mode
                    fan_mode = "medium";
                    // update Firebase
                    mDatabase.child("fan_mode").setValue(fan_mode);
                    // create update message
                    update_history("set_fan");
                }
                // if user says keywords for manual low fan mode
                else if(String.valueOf(result.get(0)).contains("low")){
                    // set fan mode seekbar
                    bubbleSeekBar4.setProgress(50);
                    // update global fan mode
                    fan_mode = "low";
                    // update Firebase
                    mDatabase.child("fan_mode").setValue(fan_mode);
                    // create update message
                    update_history("set_fan");
                }
                // if user says keywords to turn system off
                else if(String.valueOf(result.get(0)).contains("off")){
                    // set fan mode seekbar
                    bubbleSeekBar4.setProgress(0);
                    // update global fan mode
                    fan_mode = "off";
                    // update Firebase
                    mDatabase.child("fan_mode").setValue(fan_mode);
                    // create update message
                    update_history("power_off");
                }
                // if no valid fan mode was provided then indicate using a toast
                else{
                    Toast.makeText(getApplicationContext(),"Invalid fan mode provided.", Toast.LENGTH_SHORT).show();
                }
            }
            // check to see if the user wants to change the setpoint using several keywords
            else if(String.valueOf(result.get(0)).contains("temp") || String.valueOf(result.get(0)).contains("temperature") || String.valueOf(result.get(0)).contains("Celsius") || String.valueOf(result.get(0)).contains("set point")){

                try{
                    // use regex to find all integer values
                    String string_number = String.valueOf(result.get(0)).replaceAll("[^0-9]+", "");
                    // convert from string to integer
                    int number = Integer.valueOf(string_number);
                    // check to make sure temperature is between 0 and 40 C
                    if(number >= 0 && number <= mTempSeekBar.getMaxProcess()){
                        // if system is on set setpoint seekbar with temperature integer value
                        if(bubbleSeekBar4.getProgress() != 0){
                            mTempSeekBar.setCurProcess(number);
                        }
                        // perform a click on the setpoint button to update Firebase and create update message
                        setButton.performClick();
                        setButton.setPressed(true);
                        setButton.invalidate();
                        setButton.setPressed(false);
                        setButton.invalidate();
                    }
                    // if an invalid temperature is provided create a toast
                    else{
                        Toast.makeText(getApplicationContext(),"Invalid temperature provided.  Please provide an integer value.", Toast.LENGTH_SHORT).show();
                    }
                }
                // if no temperature is provided then create a toast
                catch(Exception e){
                    Toast.makeText(getApplicationContext(),"No temperature provided.", Toast.LENGTH_SHORT).show();
                }
            }
            // if no keywords were found then create a toast
            else{
                Toast.makeText(getApplicationContext(),"Invalid command.", Toast.LENGTH_SHORT).show();
            }
        }
        // if no string is found then make a toast
        catch (Exception e){
            Toast.makeText(getApplicationContext(),"No verbal instruction detected.", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * Method that creates an update message based on the update that occurred.  Each update message
     * includes a time, a user and the setting that was changed/updated.  The number of updates
     * in the Firebase is capped to 20 so if there are 20 messages in the Firebase the method
     * will determine which update is the oldest and remove that update before adding the most
     * recent update message.
     *
     * @param which_update string value that indicates what kind of update has occurred
     */

    public void update_history(String which_update){

        // string value to be written to Firebase
        String update_text = null;
        // empty array list to be used to determine which update is the oldest
        final List<Date> time_array = new ArrayList<>();
        final String currentTime = String.valueOf(Calendar.getInstance().getTime());

        // determine the update that has taken place and create a custom update message
        switch(which_update){
            case "set_temp":
                update_text = my_user_name + " set the temp to " + String.valueOf(mTempSeekBar.getCurProcess()) + "\u00B0C";
                break;
            case "set_fan":
                update_text = my_user_name + " set fan mode to " + fan_mode;
                break;
            case "power_off":
                update_text = my_user_name + " turned power " + fan_mode;
        }

        final String finalUpdate_text = update_text;

        // check the "updates" child in the Firebase
        mDatabase.child("updates")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        // create a new data/time format
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

                        // clear the time_array to make sure there are no duplicates
                        time_array.clear();

                        // iterate through all the children of "updates"
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                            // create a new calendar instance
                            Calendar first_time = Calendar.getInstance();
                            // obtain time of message in Firebase
                            String update_time = snapshot.getKey();
                            // attempt to translate string tme value to calendar object
                            try {
                                first_time.setTime(sdf.parse(update_time));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                            // add the calendar object to the list array
                            time_array.add(first_time.getTime());
                        }
                        // sort list array by time from oldest to newest
                        Collections.sort(time_array);

                        // if there are not 20 messages in Firebase
                        if(time_array.size() < 20){
                            // write new update message to Firebase
                            mDatabase.child("updates").child(currentTime).setValue(finalUpdate_text);
                        }
                        // if there are 20 messages in Firebase
                        else{
                            // remove the oldest update message
                            dataSnapshot.getRef().child(time_array.get(0).toString()).removeValue();
                            // write new update message to Firebase
                            mDatabase.child("updates").child(currentTime).setValue(finalUpdate_text);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });


    }

    /**
     * Simple method that returns a color value based on an integer temperature value.
     *
     * @param temp integer value indicating a temperature value in Celsius
     * @return color value that corresponds with input temperature value
     */

    public String set_color(int temp){

        String color_string = null;

        // if temperature is above 35 C then color is red
        if(temp > 35){
            color_string = "#FF0000";
        }
        // if between 20 and 25 C then the color is green
        else if(20 < temp && temp <= 25 ){
            color_string = "#32CD32";
        }
        // if between 25 and 35 C then the color is orange
        else if(25 < temp && temp <= 35){
            color_string = "#FAA500";
        }
        // if less then 20 C the color is blue
        else{
            color_string = "#3399FF";
        }

        // return color hex value as a string
        return color_string;

    }

    /**
     * AsyncTask that obtains current temperature at a current location using OpenWeather API
     */

    class weatherTask extends AsyncTask<String, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        protected String doInBackground(String... args) {
            // get JSON string from API
            String response = HttpRequest.excuteGet("http://api.openweathermap.org/data/2.5/weather?q="+ city + "&APPID="+ API);
            // return string to be used in onPostExecute()
            return response;
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        protected void onPostExecute(String result) {
            try {
                // turn string in JSON object
                JSONObject jsonObj = new JSONObject(result);
                // create new JSON object from "main" key
                JSONObject values = jsonObj.getJSONObject("main");
                // get value of the outside temperature from the JSON object
                // temperature is originally in kelvin so temperature is converted to Celsius
                int temp_outside = (int) Math.round((values.getInt("temp")) - 273.15);

                // update Firebase
                mDatabase.child("outside_temp").setValue(temp_outside);
                // update TextView widget text with outside temperature
                mOutsideTemp.setText(String.valueOf(temp_outside)+"\u00B0C");
                // update TextView widget text color depending on temperature
                mOutsideTemp.setTextColor(Color.parseColor(set_color(temp_outside)));

                // set the outside temperature progressbar
                localTempPB.setProgress(temp_outside);
                // set the progressbar color based on the temperature value
                if(temp_outside > 35){
                    localTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_red), PorterDuff.Mode.SRC_IN);
                }
                else if(20 < temp_outside && temp_outside <= 25 ){
                    localTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_green), PorterDuff.Mode.SRC_IN);
                }
                else if(25 < temp_outside && temp_outside <= 35){
                    localTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_orange), PorterDuff.Mode.SRC_IN);
                }
                else{
                    localTempPB.getProgressDrawable().setColorFilter(getResources().getColor(R.color.spinner_blue), PorterDuff.Mode.SRC_IN);
                }

            }
            // if task could not get the weather
            catch (JSONException e) {
                // display N/A with the TextView widget and set the progress bar to 0
                mOutsideTemp.setText("N/A");
                localTempPB.setProgress(localTempPB.getMin());

            }
        }
    }

    /**
     * Based on the thermostat fan mode this method will animate an ImageView widget at different
     * speeds.
     *
     * @param fan_setting string value that indicates a fan mode
     */

    private void animate_fan(String fan_setting){

        int animation_speed = -1;
        write_fan_to_FB = false;

        // based on thermostat fan mode set a animation duration, set the fan mode progress bar and
        // update the TextView indicating the current fan mode
        switch (fan_setting) {
            case "automatic":
                animation_speed = 2000;
                bubbleSeekBar4.setProgress(25);
                mFanMode.setText("AUTOMATIC");
                break;
            case "high":
                animation_speed = 250;
                bubbleSeekBar4.setProgress(100);
                mFanMode.setText("HIGH");
                break;
            case "medium":
                animation_speed = 500;
                mFanMode.setText("MEDIUM");
                bubbleSeekBar4.setProgress(75);
                break;
            case "low":
                animation_speed = 750;
                bubbleSeekBar4.setProgress(50);
                mFanMode.setText("LOW");
                break;
            case "off":
                animation_speed = 0;
                bubbleSeekBar4.setProgress(0);
                mFanMode.setText("OFF");
                break;
            default:
                break;
        }

        // create new rotation animation
        rotateFan = AnimationUtils.loadAnimation(this, R.anim.fan);
        // gracefully stop previous animation
        rotateFan.setRepeatCount(1);
        // set the duration of the animation (smaller number = faster rotation)
        rotateFan.setDuration(animation_speed);
        // run animation indefinitely
        rotateFan.setRepeatCount(-1);
        // start animation
        fanIV.startAnimation(rotateFan);
        write_fan_to_FB = true;
    }

    /**
     * Overridden onStart() method to make sure that the orientation is locked in portrait and
     * to recover the text to speech setting from shared preferences.
     */
    @Override
    protected void onStart() {
        super.onStart();
        // get text to speech setting
        shared_preferences = getApplicationContext().getSharedPreferences("MyPref", 0);
        if(shared_preferences.contains("tts_state")){
            tts_on_off = shared_preferences.getBoolean("tts_state", true);
        }
        // lock orientation
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    }

    /**
     * Sets the color of an ImageView of a fan based on the current temperature zone (heating, cooling
     * or stable).  Also, determines the current temperature zone of the system.
     *
     * @param temp_reading current temperature value from the Pi
     * @param set_temp current setpoint value
     * @param old_zone previous temperature zone
     * @return new temperature zone
     */

    private int set_fan_color(int temp_reading, int set_temp , int old_zone){

        int new_zone = -1;

        // cooling = 1, stable = 0, heating = 2

        // if fan seekbar indicates the system is off
        if(bubbleSeekBar4.getProgress() == 0){
            // set fan color to a dark gray
            fanIV.setBackgroundResource(R.drawable.fan);
        }
        // if system is heating
        else if(set_temp > temp_reading && old_zone != 2){
            // set fan color to red and the new zone to 2 (heating)
            fanIV.setBackgroundResource(R.drawable.fan_red);
            new_zone = 2;
        }
        // if system is cooling
        else if(set_temp < temp_reading && old_zone != 0){
            // set fan color to red and the new zone to 0 (cooling)
            fanIV.setBackgroundResource(R.drawable.fan_blue);
            new_zone = 0;
        }
        // if the system is stable
        else if(set_temp == temp_reading && old_zone != 1){
            // set fan color to green and the new zone to 1 (stable)
            fanIV.setBackgroundResource(R.drawable.fan_green);
            new_zone = 1;
        }

        // return the new temperature zone to be used later in comparisons
        return new_zone;
    }

}
