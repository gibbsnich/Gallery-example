package com.enrico.gallery.galleryapp.albums;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.enrico.gallery.galleryapp.MediaActivity;
import com.enrico.gallery.galleryapp.settings.Preferences;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;

import static android.content.Context.MODE_PRIVATE;

public class AlbumsUtils {

    private static ArrayList<Albums> allAlbums = new ArrayList<>();

    static ArrayList<Albums> getAllAlbums(Activity activity) {

        Uri externalImagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Uri internalImagesUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        Uri externalVideosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri internalVideosUri = MediaStore.Video.Media.INTERNAL_CONTENT_URI;
        String imageData = MediaStore.Images.ImageColumns.DATA;
        String imagePath = MediaStore.Images.ImageColumns.DISPLAY_NAME;
        String videoData = MediaStore.Video.VideoColumns.DATA;
        String videoPath = MediaStore.Video.VideoColumns.DISPLAY_NAME;

        allAlbums.clear();

        Cursor cursor;
        int column_index_data;

        String absolutePathOfAlbums;

        String[] ImagesProjection = {imageData, imagePath};

        cursor = activity.getContentResolver().query(externalImagesUri, ImagesProjection, null,
                null, null);

        if (cursor != null) {
            column_index_data = cursor.getColumnIndexOrThrow(imageData);

            while (cursor.moveToNext()) {

                absolutePathOfAlbums = cursor.getString(column_index_data);

                Albums media = new Albums();

                media.setAlbumsUrl(absolutePathOfAlbums);

                allAlbums.add(media);

            }
        }

        cursor = activity.getContentResolver().query(internalImagesUri, ImagesProjection, null,
                null, null);

        if (cursor != null) {
            column_index_data = cursor.getColumnIndexOrThrow(imageData);

            while (cursor.moveToNext()) {

                absolutePathOfAlbums = cursor.getString(column_index_data);

                Albums media = new Albums();

                media.setAlbumsUrl(absolutePathOfAlbums);

                allAlbums.add(media);
            }

            String[] VideosProjection = {videoData, videoPath};

            cursor = activity.getContentResolver().query(externalVideosUri, VideosProjection, null,
                    null, null);

            if (cursor != null) {
                column_index_data = cursor.getColumnIndexOrThrow(videoData);

                while (cursor.moveToNext()) {

                    absolutePathOfAlbums = cursor.getString(column_index_data);

                    Albums media = new Albums();

                    media.setAlbumsUrl(absolutePathOfAlbums);

                    allAlbums.add(media);

                }
            }

            cursor = activity.getContentResolver().query(internalVideosUri, VideosProjection, null,
                    null, null);

            if (cursor != null) {
                column_index_data = cursor.getColumnIndexOrThrow(videoData);

                while (cursor.moveToNext()) {

                    absolutePathOfAlbums = cursor.getString(column_index_data);

                    Albums media = new Albums();

                    media.setAlbumsUrl(absolutePathOfAlbums);

                    allAlbums.add(media);
                }
                cursor.close();

            }
        }
        return allAlbums;
    }

    static String getFolderName(String path) {

        File folderName = new File(path);

        return folderName.getName();

    }

    static BitmapDescriptor resizeMapIcons(final Activity activity, String imgPath, int width, int height){
        Bitmap imageBitmap = BitmapFactory.decodeFile(imgPath);
        //Bitmap imageBitmap = BitmapFactory.decodeResource(activity.getResources(),
        //        activity.getResources().getIdentifier(iconName, "drawable", activity.getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }

    private static String[] imgPaths;
    private static Map<Marker,Integer> markerPos;

    static void setupAlbums(final Activity activity, String[] veryPaths, GoogleMap googleMap) {
        imgPaths = veryPaths;
        markerPos = new HashMap<Marker,Integer>();
        LatLng ll = null;
        for (int i = 0; i < veryPaths.length; i++) {
            String mPath = veryPaths[i];
            ExifInterface exif = null;
            try {
                exif = new ExifInterface(mPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            float[] latLong = new float[2];
            boolean hasLatLong = exif.getLatLong(latLong);
            if (hasLatLong) {
                System.out.println("Latitude: " + latLong[0]);
                System.out.println("Longitude: " + latLong[1]);

                ll = new LatLng(latLong[0], latLong[1]);

                Marker m = googleMap.addMarker(new MarkerOptions()
                        .position(ll)
                        .icon(resizeMapIcons(activity, mPath, 100, 100)));
                markerPos.put(m, i);
            }
        }
        if (ll != null)
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(ll));
    }

    public static void launchMediaActivity(final Activity activity, final Marker marker) {

        Intent intent = new Intent(activity,
                MediaActivity.class);

        intent.putExtra("urls", imgPaths);
        intent.putExtra("pos", markerPos.get(marker));

        activity.startActivity(intent);

    }

    static void setupAlbums(final Activity activity, String[] veryPaths, RecyclerView recyclerView, final SectionedRecyclerViewAdapter sectionedRecyclerViewAdapter) {

        for (String path : veryPaths) {

            String[] mediaUrls = MediaFromAlbums.listMedia(path);

            if (mediaUrls.length != 0) {

                final int gridNumber = Preferences.resolveGrid(activity);

                GridLayoutManager glm = new GridLayoutManager(activity, gridNumber);
                glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        switch (sectionedRecyclerViewAdapter.getSectionItemViewType(position)) {
                            case SectionedRecyclerViewAdapter.VIEW_TYPE_HEADER:
                                return gridNumber;
                            default:
                                return 1;
                        }
                    }
                });

                recyclerView.setLayoutManager(glm);

                HeaderRecyclerViewSection headerRecyclerViewSection = new HeaderRecyclerViewSection(activity, path, mediaUrls, sectionedRecyclerViewAdapter, gridNumber);

                sectionedRecyclerViewAdapter.addSection(headerRecyclerViewSection);

            }

        }

    }

    static String[] initFolders(Activity activity, ArrayList<Albums> albumsList) {

        int mediaSize = albumsList.size();

        String[] albumsPath = new String[mediaSize];

        Set<String> paths = new HashSet<>();

        for (int i = 0; i < albumsList.size(); i++) {

            albumsPath[i] = albumsList.get(i).getAlbumsPath();

            paths.add(albumsPath[i]);
        }

        ArrayList<String> mHiddenFolders = new ArrayList<>();

        SQLiteDatabase hiddenFoldersDB = activity.openOrCreateDatabase("HIDDEN", MODE_PRIVATE, null);

        hiddenFoldersDB.execSQL("CREATE TABLE IF NOT EXISTS foldersList (id INTEGER PRIMARY KEY AUTOINCREMENT,folder varchar);");

        Cursor cursor = hiddenFoldersDB.rawQuery("SELECT * FROM foldersList;", null);

        if (cursor != null && cursor.moveToFirst()) {

            while (!cursor.isAfterLast()) {

                mHiddenFolders.add(cursor.getString(cursor.getColumnIndex("folder")));
                cursor.moveToNext();
            }
            cursor.close();
        }


        if (mHiddenFolders.size() != 0) {

            Set<String> veryHiddenFolders = new HashSet<>();

            for (String hiddenFolders : mHiddenFolders) {

                veryHiddenFolders.add(hiddenFolders);
            }

            String[] mHidden = veryHiddenFolders.toArray(new String[veryHiddenFolders.size()]);

            for (String hiddenFolders : mHidden) {

                paths.remove(hiddenFolders);
            }
        }

        return paths.toArray(new String[paths.size()]);
    }
}




