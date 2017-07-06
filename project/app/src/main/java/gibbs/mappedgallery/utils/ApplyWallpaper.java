package gibbs.mappedgallery.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import gibbs.mappedgallery.R;

import java.io.File;
import java.io.IOException;

class ApplyWallpaper {

    static void execute(Activity activity, String url) {

        new applyWallpaper(activity, url).execute();
    }

    private static class applyWallpaper extends AsyncTask<Void, Void, Void> {

        Activity activity;
        String url;
        ProgressDialog progressDialog;

        private applyWallpaper(Activity activity, String url) {
            this.activity = activity;
            this.url = url;
        }


        protected void onPreExecute() {

            progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(activity.getString(R.string.applying_wallpaper));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setIndeterminate(true);
            progressDialog.setProgressNumberFormat(null);
            progressDialog.setProgressPercentFormat(null);
            progressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {

                final WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);

                Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), Uri.fromFile(new File(url)));

                wallpaperManager.setBitmap(bitmap);

            } catch (IOException ex) {
                ex.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
        }
    }
}
