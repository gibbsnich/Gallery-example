package gibbs.mappedgallery.albums;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import gibbs.mappedgallery.utils.MD5;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

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

    static class GalleryItem implements ClusterItem {
        LatLng latLng;
        Bitmap bitmap;
        int cur, max;
        GalleryItem(LatLng ll, Bitmap bitmap, int cur, int max) {
            this.latLng = ll;
            this.bitmap = bitmap;
            this.cur = cur;
            this.max = max;
        }

        @Override
        public LatLng getPosition() {
            return latLng;
        }

        Bitmap getBitmap() {
            return bitmap;
        }

        int getCur() {
            return cur;
        }

        int getMax() {
            return max;
        }
    }

    private static class GalleryItemRenderer extends DefaultClusterRenderer<GalleryItem> {
        GalleryItemRenderer(Context context, GoogleMap map, ClusterManager<GalleryItem> clusterManager) {
            super(context, map, clusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(GalleryItem item, MarkerOptions markerOptions) {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(item.getBitmap()));
        }

        @Override
        protected void onBeforeClusterRendered(Cluster<GalleryItem> cluster, MarkerOptions markerOptions) {
            Bitmap bitmap = cluster.getItems().iterator().next().getBitmap().copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(bitmap);
            Paint p = new Paint();

            p.setColor(Color.BLACK);
            c.drawCircle(50, 50, 40, p);

            p.setColor(Color.WHITE);
            c.drawCircle(50, 50, 38, p);

            p.setColor(Color.BLACK);
            p.setTextSize(24);

            String label = Integer.toString(cluster.getSize());
            float labelWidth = p.measureText(label);
            c.drawText(label, 50 - (labelWidth/2), 62, p);
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
        }

        @Override
        protected boolean shouldRenderAsCluster(Cluster cluster) {
            return cluster.getSize() > 5;
        }
    }

    private static class populateGallery extends AsyncTask<Void, GalleryItem, Void> {

        ProgressBar progressBar;
        Activity activity;
        ArrayList<Albums> albumsList;

        private GoogleMap googleMap;
        private Map<GalleryItem,Integer> markerPos;
        private ClusterManager<GalleryItem> clusterManager;

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
            clusterManager = new ClusterManager<>(activity, googleMap);
            clusterManager.setRenderer(new GalleryItemRenderer(activity, googleMap, clusterManager));
            clusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<GalleryItem>() {
                @Override
                public boolean onClusterItemClick(GalleryItem galleryItem) {
                    AlbumsUtils.launchMediaActivity(activity, galleryItem);
                    return true;
                }
            });
            clusterManager.setOnClusterClickListener(new ClusterManager.OnClusterClickListener<GalleryItem>() {
                @Override
                public boolean onClusterClick(Cluster<GalleryItem> cluster) {
                    /*
                     Auch eine Idee: https://stackoverflow.com/questions/25395357/android-how-to-uncluster-on-single-tap-on-a-cluster-marker-maps-v2

                     LatLngBounds.Builder builder = LatLngBounds.builder();
                     for (ClusterItem item : cluster.getItems()) {
                     builder.include(item.getPosition());
                     }
                     final LatLngBounds bounds = builder.build();
                     getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                     */
                    AlbumsUtils.launchMediaActivity(activity, cluster.getItems().iterator().next());
                    return true;
                }
            });

            googleMap.setOnMarkerClickListener(clusterManager);
            googleMap.setOnInfoWindowClickListener(clusterManager);
        }

        private final static int DEFAULT_IMG_SIZE = 800;

        private Bitmap resizeAndCache(String imagePath, File cacheTargetFile) {
            Bitmap imageBitmap = BitmapFactory.decodeFile(imagePath);
            if (imageBitmap != null) {
                int width = imageBitmap.getWidth(),
                        height = imageBitmap.getHeight(),
                        targetWidth, targetHeight;

                if (width >= height) {
                    targetWidth = (int)(DEFAULT_IMG_SIZE / density);
                    targetHeight = (int)((DEFAULT_IMG_SIZE*((float)height/width)) / density);
                } else {
                    targetWidth = (int)((DEFAULT_IMG_SIZE*((float)width/height)) / density);
                    targetHeight = (int)(DEFAULT_IMG_SIZE / density);
                }

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, targetWidth, targetHeight, false);
                if (resizedBitmap != null) {
                    FileOutputStream cacheImageStream = null;
                    try {
                        cacheImageStream = new FileOutputStream(cacheTargetFile);
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
                    return resizedBitmap;
                }
            }
            return null;
        }

        private LatLng retrieveLatLng(SQLiteDatabase db, String filehash) {
            Cursor cursor = db.rawQuery("SELECT * FROM galleryCache where filehash = '" + filehash + "';", null);
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
            return ll;
        }

        private void cleanupCache(SQLiteDatabase db, String usedMd5, File cacheDir) {
            Cursor cursor = db.rawQuery("SELECT filehash FROM galleryCache where filehash not in (" + usedMd5 + ");", null);
            if (cursor != null && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    String unusedMd5 = cursor.getString(cursor.getColumnIndex("filehash"));
                    File unusedFile = new File(cacheDir, unusedMd5 + ".png");
                    unusedFile.delete();
                    cursor.moveToNext();
                }
                cursor.close();
            }
            db.execSQL("delete from galleryCache where filehash not in (" + usedMd5 + ");");
        }

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
                    LatLng ll = retrieveLatLng(galleryDB, md5);
                    if (ll == null) {
                        ll = getLatLng(mediaPath);
                        if (ll != null) {
                            galleryDB.execSQL("insert into galleryCache (filehash, lat, lng) values(?,?,?);", new Object[]{md5, ll.latitude, ll.longitude});
                        }
                    }
                    if (ll != null) {
                        File cachedImageFile = new File(imgCacheDir, md5 + ".png");
                        Bitmap mapIcon = cachedImageFile.exists() ?
                                BitmapFactory.decodeFile(cachedImageFile.getAbsolutePath()) :
                                resizeAndCache(mediaPath, cachedImageFile);
                        md5Collector.append("'");
                        md5Collector.append(md5);
                        md5Collector.append("', ");
                        publishProgress(new GalleryItem(ll, mapIcon, i, resultFiles.length));
                    }
                }
                String usedMd5 = md5Collector.toString();
                usedMd5 = usedMd5.length() > 2 ? usedMd5.substring(0, usedMd5.length() - 2) : "";
                cleanupCache(galleryDB, usedMd5, imgCacheDir);
            } finally {
                if (galleryDB != null)
                    galleryDB.close();
            }
            return null;
        }

        protected void onProgressUpdate(GalleryItem... progress) {
            GalleryItem galleryValues = progress[0];
            if (galleryValues.getPosition() != null && galleryValues.getBitmap() != null) {
                clusterManager.addItem(galleryValues);
                markerPos.put(galleryValues, galleryValues.getCur());
                progressBar.setMax(galleryValues.getMax());
                progressBar.setProgress(galleryValues.getCur());
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            clusterManager.cluster();
            AlbumsUtils.setupAlbums(resultFiles, markerPos);
            ((ViewGroup)progressBar.getParent()).removeView(progressBar); //todo: do this in a sane way..
            //adding this listener after execution so that no incomplete cluster data is rendered on zoomIn/Out
            googleMap.setOnCameraChangeListener(clusterManager);
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
