package pikkart.com.pikkarttutorial_10_17;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.pikkart.ar.recognition.IRecognitionListener;
import com.pikkart.ar.recognition.RecognitionFragment;
import com.pikkart.ar.recognition.RecognitionOptions;
import com.pikkart.ar.recognition.data.CloudRecognitionInfo;
import com.pikkart.ar.recognition.items.Marker;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IRecognitionListener {
    private int m_permissionCode = 100; // unique permission request code
    private ARView m_arView = null;

    private void initLayout() {
        setContentView(R.layout.activity_main);

        m_arView = new ARView(this);
        addContentView(m_arView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        RecognitionFragment t_cameraFragment = ((RecognitionFragment) getFragmentManager().findFragmentById(R.id.ar_fragment));
        t_cameraFragment.startRecognition(
                new RecognitionOptions(
                        RecognitionOptions.RecognitionStorage.LOCAL,
                        RecognitionOptions.RecognitionMode.CONTINUOUS_SCAN,
                        new CloudRecognitionInfo(new String[]{})
                ),
                this);
    }

    private void checkPermissions(int code) {
        // require permission to access camera, read and write external storage
        String[] permissions_required = new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE };

        // check if permissions have been granted
        List permissions_not_granted_list = new ArrayList<>();
        for (String permission : permissions_required) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissions_not_granted_list.add(permission);
            }
        }
        // permissions not granted
        if (permissions_not_granted_list.size() > 0) {
            String[] permissions = new String[permissions_not_granted_list.size()];
            permissions_not_granted_list.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, code);
        }
        else { // if all permissions have been granted
            initLayout();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        // this is the answer to our permission request (our permissioncode)
        if(requestCode==m_permissionCode) {
            // check if all have been granted
            boolean ok = true;
            for(int i=0;i<grantResults.length;++i) {
                ok = ok && (grantResults[i]==PackageManager.PERMISSION_GRANTED);
            }
            if(ok) {
                // if all have been granted, continue
                initLayout();
            }
            else {
                // exit if not all required permissions have been granted
                Toast.makeText(this, "Error: required permissions not granted!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //if not Android 6+ run the app
        if (Build.VERSION.SDK_INT < 23) {
            initLayout();
        }
        else { // otherwise ask for permissions
            checkPermissions(m_permissionCode);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        RecognitionFragment t_cameraFragment = ((RecognitionFragment) getFragmentManager().findFragmentById(R.id.ar_fragment));
        if(t_cameraFragment!=null) t_cameraFragment.startRecognition(
                new RecognitionOptions(
                        RecognitionOptions.RecognitionStorage.LOCAL,
                        RecognitionOptions.RecognitionMode.CONTINUOUS_SCAN,
                        new CloudRecognitionInfo(new String[]{})
                ), this);
        //resume our renderer
        if(m_arView!=null) m_arView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //pause our renderer and associated videos
        if(m_arView!=null) m_arView.onPause();
    }

    @Override
    public void executingCloudSearch() {
    }

    @Override
    public void cloudMarkerNotFound() {
    }

    @Override
    public void internetConnectionNeeded() {
    }

    @Override
    public void markerFound(Marker marker) {
        Toast.makeText(this, "PikkartAR: found marker " + marker.getId(),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void markerNotFound() {
    }

    @Override
    public void markerTrackingLost(String i) {
        Toast.makeText(this, "PikkartAR: lost tracking of marker " + i,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean isConnectionAvailable(Context context) {
        return false;
    }

    @Override
    public void ARLogoFound(String markerId, int code) {
    }

    @Override
    public void markerEngineToUpdate(String s) {
        
    }


}
