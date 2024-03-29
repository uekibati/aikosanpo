package jp.uekibati.aiko.sanpo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;



import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    // スタティック変数の定義
    private static String TAG = "MainActivity";
    private static int RC_SIGN_IN = 1000;

    // インスタンス変数の定義
    private SensorManager sensorManager;
    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private TextView logTextView; // ログ表示
    private Long sumOfStep = Long.valueOf(0);
    private String email;


    /**
     * Activityが生成されたときに呼ばれるメソッド
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 徒歩センサーを初期化する
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);

        // Firebase 認証を初期化する
        firebaseAuth = FirebaseAuth.getInstance();
        // Google認証を初期化する
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Firebase Firestore データベースを初期化する
        firestore = FirebaseFirestore.getInstance();

        // ログを表示するTextViewを初期化する
        logTextView = (TextView)findViewById(R.id.text_results);
    }

    /**
     * Activityが開始したときに呼ばれるメソッド
     */
    @Override
    protected void onStart() {
        super.onStart();
        // Googleアカウントでログイン済みかどうか確認する
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account==null){
            // ログインしていなかったらログインを試みるインテントを発行する
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }else {
            // ログイン済みだったらそのまま進む
            initialize(account);
        }
    }

    /**
     * Activityが破棄されるときに呼ばれるメソッド
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * インテントの結果に呼ばれるメソッド
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // ログインのIntentから戻ってくるところ
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                // ログイン成功
                GoogleSignInAccount account = task.getResult(ApiException.class);
                initialize(account);
            } catch (ApiException e) {
                // ログイン失敗
                Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
                initialize(null);
            }
        }
    }

    /**
     * センサーの値が変わったときに呼ばれるメソッド
     * @param sensorEvent
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // 一歩歩くたびにここが呼ばれる
        String text = logTextView.getText().toString();
        text = "歩いてます"+ "\n" + text;
        logTextView.setText(text);

        if (email!=null){
            // ユーザー個別の歩数値を更新する
            Map<String, Object> user = new HashMap<>();
            user.put("step", FieldValue.increment(1));
            user.put("timestamp", FieldValue.serverTimestamp());
            firestore.collection("users").document(email).update(user);
            // 全ユーザーの歩数値を更新する
            Map<String, Object> all = new HashMap<>();
            user.put("step", FieldValue.increment(1));
            user.put("timestamp", FieldValue.serverTimestamp());
            firestore.collection("all").document("all-steps").update(all);
        }

    }

    /**
     * センサーの精度が変わったときに呼ばれるメソッド
     * @param sensor
     * @param i
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    /**
     * UIを更新するためのメソッド
     * @param account
     */
    public void initialize(GoogleSignInAccount account){
        if (account==null){
            // ログイン失敗
            Log.d(TAG, "login failed...");
        }else{
            // ログイン成功
            Log.d(TAG, "login success: "+account.getEmail());

            // ログイン成功したことをユーザーにお知らせする
            final View view = this.findViewById(android.R.id.content);
            if (view == null) return;
            final Snackbar snackbar = Snackbar.make(view, account.getEmail()+"でログインしています", Snackbar.LENGTH_SHORT);
            snackbar.setAction("Logout", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    signOut();
                }
            });
            snackbar.show();
            email = account.getEmail();

            // Firebaseで認証するためのおまじない
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential: success");
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            initSnapshotListener();
                        } else {
                            Log.w(TAG, "signInWithCredential: failure", task.getException());
                            Snackbar.make(view, "Google認証に失敗しました", Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });
        }
    }

    /**
     * クラウドデータベースの徒歩値の監視を初期化するメソッド
     */
    private void initSnapshotListener(){
        final DocumentReference docRef = firestore.collection("users").document(email);
        docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot,
                                @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d(TAG, "Current data: " + snapshot.getData());
                    Log.d(TAG, "step: "+snapshot.getData().get("step"));
                    try{
                        final Long step = (Long) snapshot.getData().get("step");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView sumOfStepTextView = (TextView)findViewById(R.id.sum_of_step);
                                sumOfStepTextView.setText(step.toString());
                            }
                        });
                    }catch (Exception e1){
                        e1.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "Current data: null");
                    Map<String, Object> user = new HashMap<>();
                    user.put("step", 0);
                    user.put("timestamp", FieldValue.serverTimestamp());
                    firestore.collection("users").document(email).set(user);
                }
            }
        });
    }

    /**
     * ログアウトするメソッド
     */
    private void signOut(){
        googleSignInClient.signOut()
            .addOnCompleteListener(MainActivity.this, new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    firebaseAuth.signOut();
                }
            });
    }

}
