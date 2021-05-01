package edu.pdx.cunderla.ece558.a558_final_project;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;

public class Graph extends AppCompatActivity {

    private DatabaseReference mDatabase; // Firebase reference
    private GraphView graph; // graph
    private int count = 0; // count to scroll the graph on x-axis
    private int set_point; // setpoint integer
    private int temp; // temp integer
    private CheckBox mCheckSet, mCheckTemp; // check boxes to show/hide setpoint and temp
    private boolean check_temp = true, check_set = true; // booleans of checkbox values

    LineGraphSeries<DataPoint> series_set; // setpoint series (20 points)
    LineGraphSeries<DataPoint> series_temp; // temp series (20 points)

    ArrayList<Integer> set_array = new ArrayList<Integer>(); // array of setpoints
    ArrayList<Integer> temp_array = new ArrayList<Integer>(); // array of temperatures

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        mCheckSet = findViewById(R.id.chkSetTemp); // set up checkboxes
        mCheckTemp = findViewById(R.id.chkTemp);

        graph = (GraphView) findViewById(R.id.graph); // set up graph
        series_set = new LineGraphSeries<>(); // create series
        series_temp = new LineGraphSeries<>();
        series_set.setColor(Color.RED); // setpoint color red
        series_temp.setColor(Color.BLUE); // temp color blue
        Viewport viewport = graph.getViewport(); // viewport to customize graph
        viewport.setScrollableY(true); // scroll on y-axis instead of static size
        viewport.setXAxisBoundsManual(true); // keep x axis 20 points wide
        viewport.setMaxX(20);
        viewport.setMinX(0);
        graph.getGridLabelRenderer().setPadding(60); // padding for axis label
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false); // hide x axis label
        graph.getGridLabelRenderer().setVerticalAxisTitle("Temperature [\u00B0C]"); // set y axis label

        Intent intent = getIntent();
        set_point = intent.getIntExtra("set_point", 0); // get setpoint from main activity
        temp = intent.getIntExtra("temp", 0); // get temp from main activity

        mDatabase = FirebaseDatabase.getInstance().getReference(); // set up firebase

        if (savedInstanceState != null){ // recovering data on orientation change
            set_array = savedInstanceState.getIntegerArrayList("set_array"); // recover score
            temp_array = savedInstanceState.getIntegerArrayList("temp_array"); // recover score
            check_temp = savedInstanceState.getBoolean("temp_check"); // recover temp checkbox value
            check_set = savedInstanceState.getBoolean("set_check"); // recover setpoint checkbox value
            for(int i = 0; i < 20; i++){ // fill first 20 points of series with recovered setpoints and temps
                series_set.appendData(new DataPoint(i, set_array.get(i)), true, 20);
                series_temp.appendData(new DataPoint(i, temp_array.get(i)), true, 20);
                count++;
            }
        }
        else{ // on first create
            for(int i = 0; i < 20; i++){ // fill first 20 points of series and arrays with setpoints and temps
                series_set.appendData(new DataPoint(count, set_point), true, 20);
                set_array.add(set_point);
                series_temp.appendData(new DataPoint(count, temp), true, 20);
                temp_array.add(temp);
                count++;
            }
        }


        mCheckTemp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // if checkbox is toggled
                if (((CheckBox) v).isChecked()) { // if checked
                    check_temp = true; // set boolean to true
                    graph.removeSeries(series_temp); // clear series before adding it again
                    graph.addSeries(series_temp); // add series to graph
                }
                else{ // if unchecked
                    check_temp = false; // set boolean to false
                    graph.removeSeries(series_temp); // remove series from graph
                }
            }
        });

        mCheckSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v1) { // if checkbox is toggled
                if (((CheckBox) v1).isChecked()) { // if checked
                    check_set = true; // set boolean to true
                    graph.removeSeries(series_set); // clear series before adding it again
                    graph.addSeries(series_set); // add series to graph
                }
                else{ // if unchecked
                    check_set = false; // set boolean to false
                    graph.removeSeries(series_set); // remove series from graph
                }
            }
        });



        mDatabase.addValueEventListener(new ValueEventListener() { // if database value updated
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                set_point = (Integer) dataSnapshot.child("set_temp").getValue(Integer.class); // get setpoint
                set_array.remove(0); // remove the oldest reading (1 of 20) from array
                set_array.add(set_point); // add newest setpoint to array
                temp = (Integer) dataSnapshot.child("current_temp").getValue(Integer.class); // get temp
                temp_array.remove(0); // remove oldest reading (1 of 20) from array
                temp_array.add(temp); // add newest temp to array
                series_set.appendData(new DataPoint(count, set_point), true, 20); // add setpoint to series
                series_temp.appendData(new DataPoint(count, temp), true, 20); // add temp to series


                if(check_temp){ // if temp checkbox is checked
                    graph.removeSeries(series_temp); // clear series before adding it again
                    graph.addSeries(series_temp); // add series to graph
                }
                else{
                    graph.removeSeries(series_temp); // remove series from graph
                }

                if(check_set){
                    graph.removeSeries(series_set); // clear series before adding it again
                    graph.addSeries(series_set); // add series to graph
                }
                else{
                    graph.removeSeries(series_set); // remove series from graph
                }
                count++;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }


        });

    }

    /**
     * saves the date on orientation change
     * @param savedInstanceState
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){ // saving data on orientation change
        savedInstanceState.putIntegerArrayList("set_array", set_array); // save setpoint array
        savedInstanceState.putIntegerArrayList("temp_array", temp_array); // save temp array
        savedInstanceState.putBoolean("temp_check", check_temp); // save temp checkbox value
        savedInstanceState.putBoolean("set_check", check_set); // save setpoint checkbox value
        super.onSaveInstanceState(savedInstanceState);
    }
}
