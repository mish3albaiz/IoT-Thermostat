package com.example.meshal.ece558.final_rpi3;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 *
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends AppCompatActivity {
    private DatabaseReference mDatabase; // firebase database

    private byte pwmR_byte; // red pwm signal
    private byte pwmG_byte; // green pwm signal
    private byte pwmB_byte; // blue pwm signal

    private byte pwmM_byte; // motor pwm signal

    private int ada; // temp sensor value

    private short adas; // temp sensor word

    private I2cDevice device; // PIC device

    private Thread thread; // a thread for the infinite loop

    private Gpio direction; // motor direction through gpio

    private boolean dir = false; // direction boolean

    private boolean trigger = false; // 1 second database synchronizer timer trigger


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference(); // get database

        try{
            PeripheralManager pioService = PeripheralManager.getInstance(); // set up peripheral manager
            device = pioService.openI2cDevice("I2C1", 8); // set up I2C device (PIC at 0x08)
            direction = pioService.openGpio("BCM4"); // set gpio pin for direction
            direction.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW); // set up gpio pin
        }
        catch (Exception e){

        }

        /**
         * Database change listener
         * triggers whenever something changes in the database
         * updates RGB LED, motor, and read temp sensor
         */
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String mode = dataSnapshot.child("fan_mode").getValue(String.class); // get fanmode from database
                int temp = (Integer) dataSnapshot.child("current_temp").getValue(Integer.class); // get current temp
                int setpoint = (Integer) dataSnapshot.child("set_temp").getValue(Integer.class); // get setpoint temp

                try {
                    if(mode.equals("automatic")){
                        if(temp > setpoint){ // if system is cooling
                            pwmR_byte = 0;
                            pwmG_byte = 0;
                            pwmB_byte = 100; // blue light is on
                            int speed = (temp - setpoint) * 10; // speed is 10% PWM for every degree
                            if (speed > 100){ // capped at 100%
                                speed = 100;
                            }
                            pwmM_byte = (byte) speed;
                            dir = true; // set direction to CCW
                        }
                        else if(temp < setpoint){ // if system is heating
                            pwmR_byte = 100; // red light is on
                            pwmG_byte = 0;
                            pwmB_byte = 0;
                            int speed = (setpoint - temp) * 10; // speed is 10% PWM for every degree
                            if (speed > 100){ // capped at 100%
                                speed = 100;
                            }
                            pwmM_byte = (byte) speed;
                            dir = false; // set direction to CW
                        }
                        else{ // if temp is at setpoint
                            pwmR_byte = 0;
                            pwmG_byte = 100; // green light is on
                            pwmB_byte = 0;
                            pwmM_byte = 0; // motor is off
                            dir = false; // set direction to CW
                        }
                    }
                    else if(mode.equals("low")){
                        if(temp > setpoint){ // if system is cooling
                            pwmR_byte = 0;
                            pwmG_byte = 0;
                            pwmB_byte = 100; // blue light is on
                            pwmM_byte = 50; // set motor speed to 50%
                            dir = true; // set direction to CCW
                        }
                        else if(temp < setpoint){ // if system is heating
                            pwmR_byte = 100; // red light is on
                            pwmG_byte = 0;
                            pwmB_byte = 0;
                            pwmM_byte = 50; // set motor speed to 50%
                            dir = false; // set direction to CW
                        }
                        else{ // if temp is at setpoint
                            pwmR_byte = 0;
                            pwmG_byte = 100; // green light is on
                            pwmB_byte = 0;
                            pwmM_byte = 50; // set motor speed to 50%
                            dir = false; // set direction to CW
                        }
                    }
                    else if(mode.equals("medium")){
                        if(temp > setpoint){ // if system is cooling
                            pwmR_byte = 0;
                            pwmG_byte = 0;
                            pwmB_byte = 100; // blue light is on
                            pwmM_byte = 75; // set motor speed to 75%
                            dir = true; // set direction to CCW
                        }
                        else if(temp < setpoint){ // if system is heating
                            pwmR_byte = 100; // red light is on
                            pwmG_byte = 0;
                            pwmB_byte = 0;
                            pwmM_byte = 75; // set motor speed to 75%
                            dir = false; // set direction to CW
                        }
                        else{ // if temp is at setpoint
                            pwmR_byte = 0;
                            pwmG_byte = 100; // green light is on
                            pwmB_byte = 0;
                            pwmM_byte = 75; // set motor speed to 75%
                            dir = false; // set direction to CW
                        }
                    }
                    else if(mode.equals("high")){
                        if(temp > setpoint){ // if system is cooling
                            pwmR_byte = 0;
                            pwmG_byte = 0;
                            pwmB_byte = 100; // blue light is on
                            pwmM_byte = 100; // set motor speed to 100%
                            dir = true; // set direction to CCW
                        }
                        else if(temp < setpoint){ // if system is heating
                            pwmR_byte = 100; // red light is on
                            pwmG_byte = 0;
                            pwmB_byte = 0;
                            pwmM_byte = 100; // set motor speed to 100%
                            dir = false; // set direction to CW
                        }
                        else{ // if temp is at setpoint
                            pwmR_byte = 0;
                            pwmG_byte = 100; // green light is on
                            pwmB_byte = 0;
                            pwmM_byte = 100; // set motor speed to 100%
                            dir = false; // set direction to CW
                        }
                    }

                    else{ // if power is off
                        pwmR_byte = 0; // LED is off
                        pwmG_byte = 0;
                        pwmB_byte = 0;
                        pwmM_byte = 0; // motor is off
                        dir = false; // set direction to CW
                    }
                    device.writeRegByte(0, pwmR_byte); // write PWM values to LED
                    device.writeRegByte(1, pwmG_byte);
                    device.writeRegByte(2, pwmB_byte);
                    device.writeRegByte(3, pwmM_byte); // write PWM to motor
                    direction.setValue(dir); // set direction using GPIO
                }

                catch (Exception e){

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }


        });

        /**
         * thread loop
         * infinite loop within thread to contiously read temp sensor
         * placing the infinite loop in the thread allows the database change listener to trigger and not be blocked
         */
        thread = new Thread(new Runnable() {
            @Override
            public void run() {

                while(true) { // infinite polling loop
                    try {
                        trigger = !trigger; // timer toggler toggling
                        adas = device.readRegWord(5); // read temp sensor word
                        ada = adas; // turn word into int
                        double current_temp_double = (ada/1024.0)*(190.0) - 40.0; // convert temp from analog
                        int current_temp = (int) current_temp_double; // get rounded temp

                        if(current_temp > 0 && current_temp < 40){ // throw out inconsistent readings
                            mDatabase.child("current_temp").setValue(current_temp); // send temp reading to database
                        }
                        mDatabase.child("timer").setValue(trigger); // trigger timer if temp hasn't changed


                    }
                    catch (Exception e) {

                    }

                    try{
                        Thread.sleep(1000); // thread sleep for 1 second to update database
                    }
                    catch (InterruptedException ex){
                        ex.printStackTrace();
                    }
                }
            }
        });

        thread.start(); // start thread

    }
}
