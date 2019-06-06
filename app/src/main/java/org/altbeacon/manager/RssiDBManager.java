package org.altbeacon.manager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RssiDBManager {
    private Context mContext;
    /*选择题的集合*/
    private List<double[]> mBeanLists = new ArrayList<>();

    public RssiDBManager(Context mContext) {
        this.mContext = mContext;
    }

    //把assets目录下的db文件复制到dbpath下
    public SQLiteDatabase manage(String dbName) {
        String dbPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/databases/" + dbName;
        if (new File(dbPath).exists()) {
            File file = new File(dbPath);
            file.delete();
        }
        if (!new File(dbPath).exists()) {
            try {
                boolean flag = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/databases/").mkdirs();
                boolean newFile = new File(dbPath).createNewFile();
                try {
                    FileOutputStream out = new FileOutputStream(dbPath);
                    InputStream in = mContext.getAssets().open(dbName);
                    byte[] buffer = new byte[1024];
                    int readBytes = 0;
                    while ((readBytes = in.read(buffer)) != -1)
                        out.write(buffer, 0, readBytes);
                    in.close();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return SQLiteDatabase.openOrCreateDatabase(dbPath, null);
    }

    //查询选择题
    public List<double[]> query(SQLiteDatabase sqliteDB, String table, String[] columns, String selection, String[] selectionArgs) {
        try {
            Cursor cursor = sqliteDB.query(table, columns, selection, selectionArgs, null, null, null);
            int APNum = columns.length - 2;
            int x, y;
            while (cursor.moveToNext()) {
                double[] rssi = new double[APNum];
                for (int i = 0; i < APNum; i++) {
                    x = cursor.getInt(cursor.getColumnIndex("x"));
                    y = cursor.getInt(cursor.getColumnIndex("y"));
                    rssi[i] = cursor.getDouble(cursor.getColumnIndex("ap" + (i + 1)));
                }
                mBeanLists.add(rssi);
            }
            cursor.close();
            return mBeanLists;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //
}
