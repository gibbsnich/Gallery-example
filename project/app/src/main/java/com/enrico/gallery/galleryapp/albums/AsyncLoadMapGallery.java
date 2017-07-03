package com.enrico.gallery.galleryapp.albums;

import android.app.Activity;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;

import com.google.android.gms.maps.GoogleMap;

import java.util.ArrayList;
import java.util.List;

import io.github.luizgrp.sectionedrecyclerviewadapter.SectionedRecyclerViewAdapter;

public class AsyncLoadMapGallery {

    public static void execute(Activity activity, GoogleMap googleMap) {

        new populateGallery(activity, googleMap).execute();
    }

    private static class populateGallery extends AsyncTask<Void, Void, Void> {

        Activity activity;
        ArrayList<Albums> albumsList;

        private GoogleMap googleMap;

        private String[] resultFolders;
        private String[] resultFiles;

        private populateGallery(Activity activity, GoogleMap googleMap) {
            this.activity = activity;
            this.googleMap = googleMap;
        }


        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(Void... params) {

            albumsList = AlbumsUtils.getAllAlbums(activity);
            resultFolders = AlbumsUtils.initFolders(activity, albumsList);
            List<String> files = new ArrayList<>();
            for (String path : resultFolders) {
                String[] mediaUrls = MediaFromAlbums.listMedia(path);
                for (String mediaPath: mediaUrls) {
                    files.add(mediaPath);
                }
            }
            resultFiles = files.toArray(new String[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            AlbumsUtils.setupAlbums(activity, resultFiles, googleMap);

        }
    }
}
