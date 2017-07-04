package com.enrico.gallery.galleryapp.albums;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.enrico.gallery.galleryapp.utils.MD5;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

public class AsyncLoadMapGallery {

    public static void execute(Activity activity, GoogleMap googleMap, ProgressBar progressBar) {

        new populateGallery(activity, googleMap, progressBar).execute();
    }

    private static class GalleryValues {
        LatLng latLng;
        BitmapDescriptor bitmapDescriptor;
        int cur, max;
        GalleryValues(LatLng ll, BitmapDescriptor bd, int cur, int max) {
            this.latLng = ll;
            this.bitmapDescriptor = bd;
            this.cur = cur;
            this.max = max;
        }

        LatLng getLatLng() {
            return latLng;
        }

        BitmapDescriptor getBitmapDescriptor() {
            return bitmapDescriptor;
        }

        int getCur() {
            return cur;
        }

        int getMax() {
            return max;
        }
    }

    private static class populateGallery extends AsyncTask<Void, GalleryValues, Void> {

        ProgressBar progressBar;
        Activity activity;
        ArrayList<Albums> albumsList;

        private GoogleMap googleMap;
        private Map<Marker,Integer> markerPos;

        private String[] resultFolders;
        private String[] resultFiles;

        private float density;

        private populateGallery(Activity activity, GoogleMap googleMap, ProgressBar progressBar) {
            this.activity = activity;
            this.googleMap = googleMap;
            this.progressBar = progressBar;
        }

        protected void onPreExecute() {
            markerPos = new HashMap<>();
            DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            density = metrics.density;
        }

        private final static int DEFAULT_IMG_SIZE = 800;

        @Override
        protected Void doInBackground(Void... params) {
            //sucks, but go 2-way so we can tell the ProgressBar
            albumsList = AlbumsUtils.getAllAlbums(activity);
            resultFolders = AlbumsUtils.initFolders(activity, albumsList);
            List<String> files = new ArrayList<>();
            for (String path : resultFolders) {
                String[] mediaUrls = MediaFromAlbums.listMedia(path);
                files.addAll(Arrays.asList(mediaUrls));
            }
            resultFiles = files.toArray(new String[0]);

            SQLiteDatabase galleryDB = null;
            try {
                galleryDB = activity.openOrCreateDatabase("GALLERY", MODE_PRIVATE, null);
                galleryDB.execSQL("CREATE TABLE IF NOT EXISTS galleryCache (id INTEGER PRIMARY KEY AUTOINCREMENT,filehash varchar, lat double, lng double);");
                File imgCacheDir = activity.getCacheDir();
                StringBuilder md5Collector = new StringBuilder();
                for (int i = 0; i < resultFiles.length; i++) {
                    String mediaPath = resultFiles[i];

                    String md5 = MD5.calculateMD5(new File(mediaPath));
                    Cursor cursor = galleryDB.rawQuery("SELECT * FROM galleryCache where filehash = '" + md5 + "';", null);
                    LatLng ll = null;
                    if (cursor != null && cursor.moveToFirst()) {
                        while (!cursor.isAfterLast()) {

                            double lat = cursor.getDouble(cursor.getColumnIndex("lat")),
                                    lng = cursor.getDouble(cursor.getColumnIndex("lng"));
                            ll = new LatLng(lat, lng);

                            cursor.moveToNext();
                        }
                        cursor.close();
                    }

                    if (ll == null) {
                        ll = getLatLng(mediaPath);
                        if (ll != null) {
                            galleryDB.execSQL("insert into galleryCache (filehash, lat, lng) values(?,?,?);", new Object[]{md5, ll.latitude, ll.longitude});
                        }
                    }

                    if (ll != null) {
                        BitmapDescriptor mapIcon = null;

                        File cachedImageFile = new File(imgCacheDir, md5 + ".png");
                        if (cachedImageFile.exists()) {
                            Bitmap resizedBitmap = BitmapFactory.decodeFile(cachedImageFile.getAbsolutePath());
                            mapIcon = BitmapDescriptorFactory.fromBitmap(resizedBitmap);
                        } else {
                            Bitmap imageBitmap = BitmapFactory.decodeFile(mediaPath);
                            if (imageBitmap != null) {
                                int w = imageBitmap.getWidth(),
                                    h = imageBitmap.getHeight(),
                                    targetWidth, targetHeight;

                                if (w >= h) {
                                    h = (int)(DEFAULT_IMG_SIZE*((float)h/w));
                                    targetWidth = (int)(DEFAULT_IMG_SIZE / density);
                                    targetHeight = (int)(h / density);
                                } else {
                                    w = (int)(DEFAULT_IMG_SIZE*((float)w/h));
                                    targetWidth = (int)(w / density);
                                    targetHeight = (int)(DEFAULT_IMG_SIZE / density);
                                }

                                Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, targetWidth, targetHeight, false);
                                if (resizedBitmap != null) {
                                    FileOutputStream cacheImageStream = null;
                                    try {
                                        cacheImageStream = new FileOutputStream(cachedImageFile);
                                        resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, cacheImageStream);
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } finally {
                                        if (cacheImageStream != null) {
                                            try {
                                                cacheImageStream.flush();
                                                cacheImageStream.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }

                                    }
                                    mapIcon = BitmapDescriptorFactory.fromBitmap(resizedBitmap);
                                }
                            }
                        }
                        md5Collector.append("'");
                        md5Collector.append(md5);
                        md5Collector.append("', ");
                        //todo care about aspect ratio
                        publishProgress(new GalleryValues(ll, mapIcon, i, resultFiles.length));
                    }
                }
                String usedMd5 = md5Collector.toString();
                if (usedMd5.length() > 2)
                    usedMd5 = usedMd5.substring(0, usedMd5.length() - 2);
                else
                    usedMd5 = "";

                Cursor cursor = galleryDB.rawQuery("SELECT filehash FROM galleryCache where filehash not in (" + usedMd5 + ");", null);
                if (cursor != null && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        String unusedMd5 = cursor.getString(cursor.getColumnIndex("filehash"));
                        File unusedFile = new File(imgCacheDir, unusedMd5 + ".png");
                        if (!unusedFile.delete()) {
                           // throw new IOException("Cannot delete " + unusedFile);
                        }
                        cursor.moveToNext();
                    }
                    cursor.close();
                }

                galleryDB.execSQL("delete from galleryCache where filehash not in ("+ usedMd5 +");");
            } finally {
                if (galleryDB != null)
                    galleryDB.close();
            }
            return null;
        }

        protected void onProgressUpdate(GalleryValues... progress) {
            GalleryValues galleryValues = progress[0];
            if (galleryValues.getLatLng() != null && galleryValues.getBitmapDescriptor() != null) {
                Marker m = googleMap.addMarker(new MarkerOptions()
                        .position(progress[0].latLng)
                        .icon(progress[0].bitmapDescriptor));
                markerPos.put(m, galleryValues.getCur());
                progressBar.setMax(galleryValues.getMax());
                progressBar.setProgress(galleryValues.getCur());
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            AlbumsUtils.setupAlbums(resultFiles, markerPos);
            ((ViewGroup)progressBar.getParent()).removeView(progressBar); //todo: do this in a sane way..
        }

        private static LatLng getLatLng(String mPath) {
            ExifInterface exif = null;
            try {
                exif = new ExifInterface(mPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (exif != null) {
                float[] latLong = new float[2];
                boolean hasLatLong = exif.getLatLong(latLong);
                if (hasLatLong) {
                    return new LatLng(latLong[0], latLong[1]);
                }
            }
            return null;
        }
    }
}
