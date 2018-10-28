package is.gcb.qr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class MainActivity extends Activity {

    private boolean opened;
    private String lastValue;
    private long lastTime;

    private CameraSource source;
    private SurfaceView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //barcode detector for data matrix and QR
        BarcodeDetector detector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.DATA_MATRIX | Barcode.QR_CODE)
                .build();
        //process using anon class
        detector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {}
            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                SparseArray<Barcode> items = detections.getDetectedItems();
                if (items.size() > 0) processBarcode(items.valueAt(0));
            }
        });

        //create source that auto focuses
        source = new CameraSource.Builder(this, detector)
                .setAutoFocusEnabled(true)
                .build();
        //get surface holder and add camera source to it
        view = findViewById(R.id.surface_view);
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                startCameraSource();
                opened = false;
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                source.stop();
            }
            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {}
        });
    }

    private void processBarcode(Barcode b) {
        //if something is already open or we are reading the same barcode within 3 seconds
        if (opened ||
                (b.displayValue.equals(lastValue)
                        && System.currentTimeMillis() - lastTime < 5000)) return;
        opened = true;
        lastValue = b.displayValue;
        switch (b.valueFormat) {
            case Barcode.PHONE:
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(b.phone.number)
                        .setPositiveButton("Text", ((dialogInterface, i) ->
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + b.phone.number)))))
                        .setNegativeButton("Call", (dialogInterface, i) ->
                                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + b.phone.number))))
                        .setOnDismissListener(dialogInterface -> resetStatus())
                        .show());
                break;
            case Barcode.SMS:
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Send Message")
                        .setMessage("Send '" + b.sms.message + "' to " + b.sms.phoneNumber + "?")
                        .setPositiveButton("Send", ((dialogInterface, i) ->
                                startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:" + b.sms.phoneNumber))
                                        .putExtra("sms_body", b.sms.message))))
                        .setNegativeButton("Cancel", null)
                        .setOnDismissListener(dialogInterface -> resetStatus())
                        .show());
                break;
            case Barcode.URL:
                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(b.displayValue));
                if (openURL.resolveActivity(getPackageManager()) != null) {
                    startActivity(openURL);
                    break;
                }
            default:
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Text")
                        .setMessage(b.displayValue)
                        .setPositiveButton("Copy", (dialog, which) -> {
                            ClipData clip = ClipData.newPlainText("QR Code Text", b.displayValue);
                            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
                            Toast.makeText(this, "Copied text to clipboard.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Dismiss", null)
                        .setOnDismissListener(dialogInterface -> resetStatus())
                        .show());
        }
    }

    private void resetStatus() {
        opened = false;
        lastTime = System.currentTimeMillis();
    }

    private void startCameraSource() {
        //if camera permission, start recording, otherwise request permission
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                source.start(view.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraSource();
        }
    }
}
