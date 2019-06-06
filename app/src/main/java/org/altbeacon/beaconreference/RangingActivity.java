package org.altbeacon.beaconreference;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import android.app.Activity;

import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.altbeacon.beacon.AltBeacon;
import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.manager.RssiDBManager;
import org.altbeacon.view.IconView;

public class RangingActivity extends Activity implements BeaconConsumer {
    protected static final String TAG = "RangingActivity";
    private BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);

    private static final int XMAX = 5;
    private static final int YMAX = 6;

    private Button initBt, testBt;
    private ToggleButton startBt;
    private Switch kalmanBt;
    private TextView infoTv;
    private EditText x, y;
    private RelativeLayout kbMap;
    private ImageView iconLoc;

    private Bitmap bitmap;

    private int mapH, mapW;
    private int iconH, iconW;
    private int perH, perW;
    private double perX, perY;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranging);
    }

    private void initUI() {

        ButtonListener buttonListener = new ButtonListener();
        Button initBt = findViewById(R.id.init_bt);
        Button testBt = findViewById(R.id.test_bt);
        initBt.setOnClickListener(buttonListener);
        testBt.setOnClickListener(buttonListener);

        iconLoc = findViewById(R.id.loc_icon);
        startBt = findViewById(R.id.wknn);
        kalmanBt = findViewById(R.id.kalman);
        infoTv = findViewById(R.id.textInfo);
        x = findViewById(R.id.x);
        y = findViewById(R.id.y);

        kbMap = findViewById(R.id.kbmap);
        // 获取屏幕宽高,转换图与屏幕大小
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) kbMap.getLayoutParams();
        layoutParams.width = screenWidth;
        bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.kbmap240);
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();
        float scale = (float) screenWidth / width;
        layoutParams.height = (int) (height * scale);
        kbMap.setLayoutParams(layoutParams);


        // 设置start监听事件
        startBt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    initBleMap();
                    if ("".equals(x.getText().toString()) || "".equals(y.getText().toString())) {
                        // 测出第一个点
                        flag = FIRST_SCAN;
                        mWifiManager.startScan();
                    } else {
                        curLoc = new double[]{Integer.parseInt(x.getText().toString()),
                                Integer.parseInt(y.getText().toString())};
                        flag = START_SCAN;
                        mWifiManager.startScan();
                    }
                } else {
                    flag = STOP_SCAN;
                }
            }
        });
    }

    class ButtonListener implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.init_bt:
                    //获取地图及图表宽高,只有在onCreate执行后才行
                    mapH = kbMap.getHeight();
                    mapW = kbMap.getWidth();
                    iconH = iconLoc.getHeight();
                    iconW = iconLoc.getWidth();
                    perH = mapH / (YMAX + 1);
                    perW = mapW / XMAX;
                    perX = perW - iconW / 2;
                    perY = perH - iconH / 2;
                    readFingerDatabase(APNUM);

                    break;
                default:
                    break;
            }
        }
    }
    private void readFingerDatabase(int APNum) {
        RssiDBManager rssiDBManager = new RssiDBManager(this);
        SQLiteDatabase sqLiteDatabase = rssiDBManager.manage("esp_buy_map.db");
        String[] columns = new String[APNum + 2];
        columns[0] = "x";
        columns[1] = "y";
        for (int i = 1; i <= APNum; i++) {
            columns[i+1] = "ap" + i;
        }
        List<double[]> rssiList = rssiDBManager.query(sqLiteDatabase, "new_rssi_ave_sort", columns, null, null);
        int LocNum = rssiList.size();
        double[][] rssiArray = new double[LocNum][APNum];
        rssiArray = rssiList.toArray(rssiArray);
        rssiAve = new double[XMAX][YMAX][rssiArray[0].length];
        int maxNum = Math.max(XMAX, YMAX);
        for (int i = 0; i < XMAX; i++) {
            for (int j = 0; j < YMAX; j++) {
                rssiAve[i][j] = rssiArray[i * maxNum + j];
            }
        }
        Log.e("测试", "rssi[1][1]: " + Arrays.toString(rssiAve[0][0]));
        sqLiteDatabase.close();
    }

    @Override 
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override 
    protected void onPause() {
        super.onPause();
        beaconManager.unbind(this);
    }

    @Override 
    protected void onResume() {
        super.onResume();
        beaconManager.bind(this);
    }



    @Override
    public void onBeaconServiceConnect() {

        RangeNotifier rangeNotifier = new RangeNotifier() {
           @Override
           public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
               if (beacons.size() > 0) {
                   for (Beacon beacon : beacons) {
                       Log.d(TAG, "uuid: " + beacon.getId1());
                   }
                   Log.d(TAG, "didRangeBeaconsInRegion called with beacon count:  " + beacons.size());
                   Beacon firstBeacon = beacons.iterator().next();

                   logToDisplay("The first beacon " + firstBeacon.toString() + " is about " + firstBeacon.getDistance() + " meters away.");
               }
           }

        };
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
            beaconManager.addRangeNotifier(rangeNotifier);
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
            beaconManager.addRangeNotifier(rangeNotifier);
        } catch (RemoteException e) {   }
    }

    private void logToDisplay(final String line) {
//        runOnUiThread(new Runnable() {
//            public void run() {
//                EditText editText = (EditText)RangingActivity.this.findViewById(R.id.rangingText);
//                editText.append(line+"\n");
//            }
//        });
    }
}
