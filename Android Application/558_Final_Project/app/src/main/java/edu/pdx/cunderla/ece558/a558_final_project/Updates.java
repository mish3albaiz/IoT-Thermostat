package edu.pdx.cunderla.ece558.a558_final_project;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The updates activity is responsible for displaying the most recent 20 updates of the thermostat
 * system.  The activity not only displays the update messages but also gives the user to sort the
 * messages a couple ways as well clear the entire "updates" child using a clear button.  When
 * displaying the update messages each message is color coordinated with a provided legend in order
 * to aid readability.
 */

public class Updates extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private Spinner order_spinner;
    private Button clearButton;
    private List<Date> time_array = new ArrayList<>();
    private List<String> update_array = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        // associating widgets and Firebase with their respective IDs
        mDatabase = FirebaseDatabase.getInstance().getReference();
        order_spinner = findViewById(R.id.order_spinner);
        clearButton = findViewById(R.id.clear_button);

        // array adapter for spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.scroll_view_spinner, android.R.layout.simple_spinner_item);

        // settings the spinner adapter
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // implementing the adapter on spinner
        order_spinner.setAdapter(adapter);

        // initializing and linking listener for order_spinner
        order_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {

                int index = arg0.getSelectedItemPosition();

                switch(index){
                    // if index 0 is selected then organize updates from newest to oldest
                    case 0:
                        set_scroll_view(true);
                        break;
                    // if index 1 is selected then organize updates from oldest to newest
                    case 1:
                        set_scroll_view(false);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + index);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        // initializing and linking listener for clear button
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // using a dialog box to ask the user if they really want to clear the updates history
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            // if the user chooses yes then clear the child in the Firebase holding all the updates
                            case DialogInterface.BUTTON_POSITIVE:
                                mDatabase.child("updates").removeValue();
                                break;
                            // if the user chooses no then do nothing and exit dialog
                            case DialogInterface.BUTTON_NEGATIVE:
                                //No button clicked
                                break;
                        }
                    }
                };

                // parameters for dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(Updates.this);
                builder.setTitle("Warning!").setIcon(R.mipmap.ic_launcher_home_round).setMessage("Are you sure you want to clear all updates from Firebase?").setPositiveButton("Yes", dialogClickListener)
                        .setNegativeButton("No", dialogClickListener).show();
            }
        });

        // listener for the updates child of the Firebase
        FirebaseDatabase.getInstance().getReference().child("updates")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {

                        // defining a new data format that is more legible than the default saved timestamp
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

                        // clear both arrays to make sure there is no duplicate data
                        time_array.clear();
                        update_array.clear();

                        // iterate through all the children in "users" Firebase child
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()){

                            // calendar variable used to help translate a string form Firebase into a calendar object
                            Calendar first_time = Calendar.getInstance();

                            // obtain time from user child
                            String update_time = snapshot.getKey();

                            try {
                                // attempt to convert time string from Firebase to calendar object
                                first_time.setTime(sdf.parse(update_time));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                            // sort calendar objects by time
                            time_array.add(first_time.getTime());


                        }

                        // sorting keys by time
                        Collections.sort(time_array);

                        // iterate through time_array to match keys (time values) with their values (updates)
                        for(int j = 0;j < time_array.size();j++){
                            // obtain the update message corresponding to current time in time_array by using the Firebase key to access the value
                            String update_text = dataSnapshot.child(String.valueOf(time_array.get(j))).getValue(String.class);
                            // add update message to update_text array at same index as corresponding time in time_array
                            update_array.add(update_text);
                        }

                        // check to see the selected spinner value
                        switch(order_spinner.getSelectedItemPosition()){
                            // if index 0, sort messages from newest to oldest
                            case 0:
                                set_scroll_view(true);
                                break;
                            // if index 1, sort messages from oldest to newest
                            case 1:
                                set_scroll_view(false);
                                break;
                        }
                    }


                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });

    }


    /**
     * This method reads the "updates" child from the Firebase and constructs a sorted list array
     * based on time from oldest to youngest.  The array is then displayed dynamically within
     * a scrollview widget.  Update messages indicate who change what setting at what time.  Update
     * messages are color coordinated in order to improve readability.  The messages can be sorted
     * either from newest to oldest or oldest to newest using a boolean value.
     *
     * @param new_updates_first boolean value which dictates whether update messages are displayed
     *                          from newest to oldest (true) or oldest to newest (false)
     */

    public void set_scroll_view(Boolean new_updates_first){

        final LinearLayout test = findViewById(R.id.test);
        // clear the scrollview in order to repopulate it
        test.removeAllViews();
        // defining an easy to read date format
        SimpleDateFormat dateFormat = new SimpleDateFormat("M/dd/yyyy");
        // defining an east to read time format
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
        // strings that contain keywords in update messages, used to color coordinate messages to what setting that was changed
        String power= "power";
        String temp = "temp";
        String fan = "fan";
        String member = "member";
        // creating a linear layout to hold each update
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // adding margins to each update message's linear layout
        params.setMargins(10,20,10,20);

        if(!new_updates_first){

            // iterate through time_array from smallest to largest index (sort from oldest to newest update messages)
            for(int i = 0; i < time_array.size(); i++){

                // creating a TextView to hold the time an update occurred
                TextView TimeTextView = new TextView(Updates.this);
                // creating a TextView to hold the update message text
                TextView UpdateTextView = new TextView(Updates.this);
                // creating a divider line to separate update messages in scroll view widget
                View dividerLine = new View(Updates.this);

                // setting divider line width to 1 and to be the color black
                dividerLine.setMinimumHeight(1);
                dividerLine.setBackgroundColor(Color.parseColor("#000000"));
                dividerLine.setLayoutParams(params);

                // setting text size to 15
                TimeTextView.setTextSize(15);
                // setting gravity to center
                TimeTextView.setGravity(1);

                // creating an easy to read time string for when the update occurred
                String time_string = String.valueOf(timeFormat.format(time_array.get(i))) + " (" + String.valueOf(dateFormat.format(time_array.get(i))) + ")";

                // using TextView widget to display formatted time string
                TimeTextView.setText(time_string);

                // obtaining update message text and displaying it with a TextView widget
                UpdateTextView.setText(String.valueOf(update_array.get(i)));
                // setting gravity to be center
                UpdateTextView.setGravity(1);


                // color coordinate messages depending on what type of update it is
                // if message relates to power, set text color to red
                if (String.valueOf(update_array.get(i)).toLowerCase().indexOf(power.toLowerCase()) != -1) {
                    UpdateTextView.setTextColor(Color.parseColor("#FF0000"));
                    TimeTextView.setTextColor(Color.parseColor("#FF0000"));
                }
                // if message relates to a change in setpoint, set text color to green
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(temp.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#2A9637"));
                    TimeTextView.setTextColor(Color.parseColor("#2A9637"));
                }
                // if message relates to a change in fan mode, set text color to blue
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(fan.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#0000FF"));
                    TimeTextView.setTextColor(Color.parseColor("#0000FF"));
                }
                // if message relates an addition of a new user, set text color to purple
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(member.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#892A96"));
                    TimeTextView.setTextColor(Color.parseColor("#892A96"));
                }

                // add the TextView containing the time to the linear layout
                test.addView(TimeTextView);
                // add the TextView containing the update message to the linear layout
                test.addView(UpdateTextView);

                // if this is not the last value in the time_array then add a divider line under the two TextView objects
                if(i != time_array.size() - 1){
                    test.addView(dividerLine);
                }

            }

        }
        else{
            // iterate through time_array from largest to smallest index (sort from newest to oldest update messages)
            for(int i = time_array.size()-1; i >= 0; i--){

                // creating a TextView to hold the time an update occurred
                TextView TimeTextView = new TextView(Updates.this);
                // creating a TextView to hold the update message text
                TextView UpdateTextView = new TextView(Updates.this);
                // creating a divider line to separate update messages in scroll view widget
                View dividerLine = new View(Updates.this);

                // setting divider line width to 1 and to be the color black
                dividerLine.setMinimumHeight(1);
                dividerLine.setBackgroundColor(Color.parseColor("#000000"));
                dividerLine.setLayoutParams(params);

                // creating an easy to read time string for when the update occurred
                String time_string = String.valueOf(timeFormat.format(time_array.get(i))) + " (" + String.valueOf(dateFormat.format(time_array.get(i))) + ")";

                // using TextView widget to display formatted time string
                TimeTextView.setText(String.valueOf(time_string));
                // text size of 15
                TimeTextView.setTextSize(15);
                // center gravity
                TimeTextView.setGravity(1);

                // obtaining update message text and displaying it with a TextView widget
                UpdateTextView.setText(String.valueOf(update_array.get(i)));
                UpdateTextView.setGravity(1);

                // color coordinate messages depending on what type of update it is
                // if message relates to power, set text color to red
                if (String.valueOf(update_array.get(i)).toLowerCase().indexOf(power.toLowerCase()) != -1) {
                    UpdateTextView.setTextColor(Color.parseColor("#FF0000"));
                    TimeTextView.setTextColor(Color.parseColor("#FF0000"));
                }
                // if message relates to a change in setpoint, set text color to green
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(temp.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#2A9637"));
                    TimeTextView.setTextColor(Color.parseColor("#2A9637"));
                }
                // if message relates to a change in fan mode, set text color to blue
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(fan.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#0000FF"));
                    TimeTextView.setTextColor(Color.parseColor("#0000FF"));
                }
                // if message relates an addition of a new user, set text color to purple
                else if(String.valueOf(update_array.get(i)).toLowerCase().indexOf(member.toLowerCase()) != -1){
                    UpdateTextView.setTextColor(Color.parseColor("#892A96"));
                    TimeTextView.setTextColor(Color.parseColor("#892A96"));
                }

                // add the TextView containing the time to the linear layout
                test.addView(TimeTextView);
                // add the TextView containing the update message to the linear layout
                test.addView(UpdateTextView);

                // if this is not the first value in the time_array then add a divider line under the two TextView objects
                if(i != 0){
                    test.addView(dividerLine);
                }

            }
        }
    }

}
