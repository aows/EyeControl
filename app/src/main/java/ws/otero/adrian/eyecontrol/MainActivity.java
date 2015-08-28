package ws.otero.adrian.eyecontrol;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.IOException;

import de.greenrobot.event.EventBus;

public class MainActivity extends AppCompatActivity {

    static final int RC_HANDLE_CAMERA_PERM = 2;
    static final float PROBABILITY = 0.75f;
    static final int SPEED_MULTIPLIER = 5;


    FaceDetector detector;
    CameraSource source;

    int leftRating;
    int rightRating;

    // views
    View left;
    View right;
    RecyclerView myList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        left = findViewById(R.id.left);
        right = findViewById(R.id.right);

        // setup the list
        myList = (RecyclerView) findViewById(R.id.my_list);
        myList.setLayoutManager(new LinearLayoutManager(this));
        myList.setAdapter(new MyListAdapter());

        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

    }

    private void createCameraSource() {
        detector = new FaceDetector.Builder(this)
                .setTrackingEnabled(true)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE)
                .build();
        detector.setProcessor(new MultiProcessor.Builder<>(new FaceTrackerFactory()).build());

        source = new CameraSource.Builder(this, detector)
                .setRequestedPreviewSize(1024, 768)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    private void requestCameraPermission() {
        final String[] permissions = new String[]{Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
            return;
        }

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("EyeControl")
                .setMessage("No camera permission")
                .setPositiveButton("Ok", listener)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        EventBus.getDefault().register(this);

        try {
            if (source != null)
                source.start();

        } catch (IOException e) {
            Log.e("TAG", "Oops", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);

        if (source != null)
            source.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detector != null)
            detector.release();
        if (source != null)
            source.release();
    }

    // event bus events
    private class LeftEyeOpenEvent {
    }

    private class RightEyeOpenEvent {
    }

    private class LeftEyeClosedEvent {
    }

    private class RightEyeClosedEvent {
    }

    public void onEventMainThread(LeftEyeOpenEvent e) {
        left.setBackgroundColor(getColor(R.color.green));
    }

    public void onEventMainThread(RightEyeOpenEvent e) {
        right.setBackgroundColor(getColor(R.color.green));
    }

    public void onEventMainThread(LeftEyeClosedEvent e) {
        left.setBackgroundColor(getColor(R.color.red));
        myList.scrollBy(0, -leftRating * SPEED_MULTIPLIER);
    }

    public void onEventMainThread(RightEyeClosedEvent e) {
        right.setBackgroundColor(getColor(R.color.red));
        myList.scrollBy(0, rightRating * SPEED_MULTIPLIER);
    }

    // face tracker
    private class FaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new FaceTracker();
        }
    }

    private class FaceTracker extends Tracker<Face> {

        @Override
        public void onNewItem(int faceId, Face item) {
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, final Face face) {
            if (face.getIsLeftEyeOpenProbability() <= 0 || face.getIsRightEyeOpenProbability() <= 0) {
                leftRating = 0;
                rightRating = 0;
                return;
            }
            if (face.getIsRightEyeOpenProbability() < PROBABILITY) {
                rightRating++;
                if (rightRating > 3) {
                    EventBus.getDefault().post(new RightEyeClosedEvent());
                }
            } else {
                rightRating = 0;
                EventBus.getDefault().post(new RightEyeOpenEvent());
            }
            if (face.getIsLeftEyeOpenProbability() < PROBABILITY) {
                leftRating++;
                if (leftRating > 3) {
                    EventBus.getDefault().post(new LeftEyeClosedEvent());
                }
            } else {
                leftRating = 0;
                EventBus.getDefault().post(new LeftEyeOpenEvent());
            }
        }


        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
        }

        @Override
        public void onDone() {
        }
    }

    private class MyListAdapter extends RecyclerView.Adapter<MyListAdapter.ViewHolder> {

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            public ViewHolder(View v) {
                super(v);
                text = (TextView) v;
            }
        }

        @Override
        public int getItemCount() {
            return 100;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_item, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.text.setText("Item " + position);
        }
    }

}
