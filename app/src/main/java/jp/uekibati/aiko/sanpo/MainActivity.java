package jp.uekibati.aiko.sanpo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.HistoryClient;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    public static int REQUEST_OAUTH_REQUEST_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("MainActivity", "onCreate");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                String[] permissions = { Manifest.permission.ACTIVITY_RECOGNITION };
                ActivityCompat.requestPermissions(this,
                        permissions,
                        REQUEST_OAUTH_REQUEST_CODE);
            }
        }else{
            FitnessOptions fitnessOptions = FitnessOptions.builder()
                    .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                    .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                    .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .build();

            if (!GoogleSignIn.hasPermissions(
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions)) {
                Log.d("MainActivity", "hasPermissions false!!");
                GoogleSignIn.requestPermissions(
                        this,
                        REQUEST_OAUTH_REQUEST_CODE,
                        GoogleSignIn.getLastSignedInAccount(this),
                        fitnessOptions
                );
            } else {
                Log.d("MainActivity", "hasPermissions true!!");
                readData();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        Log.d("MainActivity", "onRequestPermissionsResult"+requestCode);
        switch (requestCode) {
            case 1000: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.d("MainActivity", "permission was granted");
                    readData();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.d("MainActivity", "permission denied, boo!");
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    public void readData() {
        Log.d("MainActivity", "readData...");
        Date now = new Date();
        long start = now.getTime()-(60*60*24);
        long end = now.getTime();

        DataReadRequest.Builder builder = new DataReadRequest.Builder();
        builder.setTimeRange(start, end, TimeUnit.SECONDS);
        builder.read(DataType.TYPE_STEP_COUNT_DELTA);
        DataReadRequest request = builder.build();

        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .readData(request)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.d("MainActivity", dataReadResponse.getDataSets().toString());
                    }
                });
    }
}
