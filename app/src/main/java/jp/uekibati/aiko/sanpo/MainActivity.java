package jp.uekibati.aiko.sanpo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.HistoryClient;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private PendingIntent pendingIntent;
    private SensorManager sensorManager;
    private TextView _textResult; // 認識結果を表示するところ
    private Integer sumOfStep = 0;

    // 認識結果は PendingIntent で通知してくれる
    //  PendingIntent に、Service を起動する Intent を仕込んでおいて、
    //  認識結果の取得はそっちで行う。 > ReceiveRecognitionIntentService.java
    private PendingIntent _receiveRecognitionIntent;

    // ReceiveRecognitionIntentService で取得した認識結果は、Broadcast で通知されるので、
    // それを受け取る Receiver 。ここで画面に認識結果を表示する。
    private final BroadcastReceiver _receiveFromIntentService = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int activityType = intent.getIntExtra("activity_type", 0);
            final int confidence = intent.getIntExtra("confidence", -1);
            final long time = intent.getLongExtra("time", 0);

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String text = _textResult.getText().toString();
                    text = DateFormat.format("hh:mm:ss.sss", time) + " - "
                            + getNameFromType(activityType) + " (" +
                            + confidence + "%)" + "\n" + text;

                    _textResult.setText(text);
                }
            });
        }

        // http://developer.android.com/training/location/activity-recognition.html
        // からパクってきた関数
        /**
         * Map detected activity types to strings
         *@param activityType The detected activity type
         *@return A user-readable name for the type
         */
        private String getNameFromType(int activityType) {
            switch(activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return "自動車で移動中";
                case DetectedActivity.ON_BICYCLE:
                    return "自転車で移動中";
                case DetectedActivity.ON_FOOT:
                    return "徒歩で移動中";
                case DetectedActivity.STILL:
                    return "停止中";
                case DetectedActivity.UNKNOWN:
                    return "不明";
                case DetectedActivity.TILTING:
                    return "不明";
            }
            return "unknown - " + activityType;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);

        _textResult = (TextView)findViewById(R.id.text_results);

        // IntentService から Broadcast される認識結果を受け取るための Receiver を登録しておく
        registerReceiver(_receiveFromIntentService, new IntentFilter("receive_recognition"));

        final Button buttonStart = (Button)findViewById(R.id.button_start);
        startReckoning();
        buttonStart.setText("Stop");
        buttonStart.setOnClickListener(new View.OnClickListener() {
            private boolean _isStarted = false;

            @Override
            public void onClick(View v) {

                if (!_isStarted) {
                    startReckoning();
                    buttonStart.setText("Stop");
                } else {
                    stopReckoning();
                    buttonStart.setText("Start");
                }

                _isStarted = !_isStarted;
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopReckoning();
        // ConnectionCallbacks.onDisconnected が呼ばれるまで待った方がいい気がする
        unregisterReceiver(_receiveFromIntentService);
        super.onDestroy();
    }

    private void startReckoning() {
        ActivityRecognitionClient activityRecognitionClient = ActivityRecognition.getClient(this);

        Intent intent = new Intent(this, ReceiveRecognitionIntentService.class);
        pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Task task = activityRecognitionClient.requestActivityUpdates(180_000L, pendingIntent);

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Toast.makeText(MainActivity.this, "行動認識の開始に成功しました", Toast.LENGTH_LONG).show();
            }
        });
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "行動認識の開始に失敗しました", Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });

    }

    private void stopReckoning() {
        ActivityRecognitionClient activityRecognitionClient = ActivityRecognition.getClient(this);
        Task<Void> task = activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent);
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                pendingIntent.cancel();
                Toast.makeText(MainActivity.this, "行動認識の停止に成功しました", Toast.LENGTH_LONG).show();
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "行動認識の停止に失敗しました", Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        String text = _textResult.getText().toString();
        text = "歩いてます"+ "\n" + text;
        _textResult.setText(text);
        sumOfStep=sumOfStep+1;
        TextView sumOfStepTextView = (TextView)findViewById(R.id.sum_of_step);
        sumOfStepTextView.setText(String.valueOf(sumOfStep));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
