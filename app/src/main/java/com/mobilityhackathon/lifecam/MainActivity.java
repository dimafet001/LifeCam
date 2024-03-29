package com.mobilityhackathon.lifecam;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Intent i = new Intent(getApplicationContext(), UserHomeActivity.class);
//                Intent i = new Intent(getApplicationContext(), AccidentDescActivity.class);
                startActivity(i);
            }
        }, 1000);
    }
}
