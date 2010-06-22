/*******************************************************************************
 * Copyright (c) 1999-2010, Vodafone Group Services
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright 
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above 
 *       copyright notice, this list of conditions and the following 
 *       disclaimer in the documentation and/or other materials provided 
 *       with the distribution.
 *     * Neither the name of Vodafone Group Services nor the names of its 
 *       contributors may be used to endorse or promote products derived 
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE.
 ******************************************************************************/
package com.vodafone.android.navigation.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.vodafone.android.navigation.NavigatorApplication;
import com.vodafone.android.navigation.R;
import com.vodafone.android.navigation.mapobject.AndroidMapObject;
import com.vodafone.android.navigation.mapobject.LandmarkMapObject;
import com.vodafone.android.navigation.persistence.ApplicationSettings;
import com.wayfinder.pal.android.network.http.HttpConfigurationInterface;

public class ImageDownloader implements Runnable {

    public static final String IMAGE_NAME_DEFAULT = "vf_search_heading_addresses";
    public static final String IMAGE_NAME_EMPTY = "empty_image";

    private static final String TAG = "ImageDownloader";
    private static final String LIST_SUFFIX = "_L";
    private static ImageDownloader INSTANCE;

    private Thread downloadThread;
    private HashMap<String, Bitmap> cache = new HashMap<String, Bitmap>();
    private ArrayList<String> queue = new ArrayList<String>();
    private HashMap<String, QueueItem> mapping = new HashMap<String, QueueItem>();
    private NavigatorApplication app;
    private int failCount;
    private boolean exit;

    private ImageDownloader(HttpConfigurationInterface httpConfig) {
        m_httpConfig = httpConfig;
    }

    private final HttpConfigurationInterface m_httpConfig;
    public static void create(HttpConfigurationInterface httpConfig) {
        INSTANCE = new ImageDownloader(httpConfig); 
    }

    public static final ImageDownloader get() {
        return INSTANCE;
    }

    /**
     * returns bitmap for imageName if already downloaded and cached. Otherwise
     * it will queue the downloading of the bitmap and then call
     * ImageDownloadListener.onImageDownloaded. If the bitmap was downloaded and
     * cached already, the bitmap will be returned without the listener being
     * called
     * 
     * @param imageName
     * @param mapObject
     * @param listener
     * @return bitmap if already downloaded and cached
     */
    public synchronized Bitmap queueDownload(Context context, String imageName, AndroidMapObject mapObject, ImageDownloadListener listener) {
        if(imageName != null) {
        	if(imageName == null || "".equals(imageName)){
        		Bitmap defImage =  BitmapFactory.decodeResource(context.getResources(), R.drawable.default_40);
        		int imageMaxWidth = LandmarkMapObject.getImageMaxWidth(this.app);
                int imageMaxHeight = LandmarkMapObject.getImageMaxHeight(this.app);
        		Bitmap scaledBitmap = ResourceUtil.scale(defImage, imageMaxWidth, imageMaxHeight);
        		AndroidMapObject[] mapObjects = {mapObject};
        		listener.onImageDownloaded(scaledBitmap, defImage, imageName, mapObjects);
        		return null;
        	}
            Bitmap bitmap = this.getImage(context, imageName, false);
            if(bitmap != null) {
                return bitmap;
            }
    
            this.startThread();
            
            QueueItem queueItem = this.mapping.get(imageName);
            if(queueItem == null) {
                queueItem = new QueueItem(imageName);
                this.mapping.put(imageName, queueItem);
            }
            queueItem.addMapObject(mapObject);
            queueItem.addListener(listener);
    
            if (!this.queue.contains(imageName)) {
                this.queue.add(imageName);
                this.notifyAll();
            }
        }
        return null;
    }

    /**
     * returns a cached image, or null if image is not cached
     * 
     * @param imageName
     * @return returns a cached image, or null if image is not cached
     */
    public Bitmap getImage(Context context, String imageName, boolean useImageInList) {
        Bitmap bitmap = null;
        if(imageName != null && !"".equals(imageName)) {
            this.app = (NavigatorApplication) context.getApplicationContext();
    
            if(useImageInList) {
                bitmap = this.cache.get(imageName + LIST_SUFFIX);
            }
            else {
                bitmap = this.cache.get(imageName);
            }
            
            if (bitmap == null || bitmap.isRecycled()) {
                this.cache.remove(imageName);
                bitmap = this.readBitmapFromFile(imageName, useImageInList);
            }
        } else {
        	bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_40);
        }
        return bitmap;
    }

    /**
     * @return returns a new scaled bitmap
     */
    public Bitmap getScaledImage(Context context, String imageName, int imageMaxWidth, int imageMaxHeight) {
    	Bitmap bitmap = this.getImage(context, imageName, true);
        if(bitmap != null) {
            bitmap = ResourceUtil.scale(bitmap, imageMaxWidth, imageMaxHeight);
        }
        
        return bitmap;
    }

    public void run() {
        while (!this.exit) {
            String imageName = this.getNextImageName();
            this.downloadImage(imageName);
        }
        
        this.downloadThread = null;
        if(this.exit) {
            Log.e(TAG, "download-thread has stopped");
        }
    }

    private void startThread() {
        if(this.downloadThread == null) {
            Log.i(TAG, "download-thread is being started");
            this.exit = false;
            this.downloadThread = new Thread(this, TAG);
            this.downloadThread.start();
        }
    }

    private void downloadImage(String imageName) {
        HttpURLConnection connection = null;
        InputStream in = null;
        try {
            String serverUrl = this.app.getServerAddress();
            URL url = new URL(serverUrl + "TMap/B;" + imageName + ".png");

            Log.i("ImageDownloader", "downloadImage() URL: " + url);

            Proxy proxy = m_httpConfig.getProxy();
            Log.d(TAG, "proxy: " + proxy.toString());
            connection = (HttpURLConnection) url.openConnection(proxy);
            
            connection.addRequestProperty("User-Agent", ApplicationSettings
                    .get().getClientId()
                    + "/" + this.app.getVersion());
            connection.addRequestProperty("Accept", "*/*");

            connection.setDoInput(true);
            connection.connect();
            in = connection.getInputStream();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = in.read(buffer);
            while(read > 0) {
                baos.write(buffer, 0, read);
                read = in.read(buffer);
            }
            
            byte[] fileData = baos.toByteArray();
            this.writeBitmapToFile(imageName, fileData);
            
            ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
            Bitmap origBitmap = BitmapFactory.decodeStream(bais);

            this.cache.put(imageName + LIST_SUFFIX, origBitmap);

            this.queue.remove(imageName);

            int imageMaxWidth = LandmarkMapObject.getImageMaxWidth(this.app);
            int imageMaxHeight = LandmarkMapObject.getImageMaxHeight(this.app);
            Bitmap scaledBitmap = ResourceUtil.scale(origBitmap, imageMaxWidth, imageMaxHeight);
            this.cache.put(imageName, scaledBitmap);

            QueueItem queueItem = this.mapping.get(imageName);
            if(queueItem != null) {
                queueItem.notifyListeners(scaledBitmap, origBitmap);
            }
            
            this.failCount = 0;
        } catch (Exception e) {
            Log.e(TAG, "Downloading failed [" + imageName + "]: " + e);
            e.printStackTrace();
            
            //if we fail downloading 10 times in a row we shot down the thread
            this.failCount ++;
            if(this.failCount > 10) {
                this.exit = true;
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeBitmapToFile(String imageName, byte[] fileData) {
        Log.i("ImageDownloader", "writeBitmapToFile() saving bitmap: " + imageName);
        FileOutputStream out = null;
        try {
            out = this.app.openFileOutput(imageName, Context.MODE_PRIVATE);
            out.write(fileData);
            out.flush();
            Log.i("ImageDownloader", "writeBitmapToFile() bitmap saved: " + imageName);
        }
        catch(IOException e) {
            Log.e("ImageDownloader", "writeBitmapToFile() error when saving: " + e);
            e.printStackTrace();
        }
        finally {
            if(out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
                out = null;
            }
        }
    }

    private Bitmap readBitmapFromFile(String imageName, boolean useImageInList) {
        Log.i("ImageDownloader", "readBitmapFromFile() reading bitmap: " + imageName);
        if(imageName != null) {
            InputStream in = null;
            try {
                in = this.app.openFileInput(imageName);
                
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                this.cache.put(imageName + LIST_SUFFIX, bitmap);
    
                int imageMaxWidth = LandmarkMapObject.getImageMaxWidth(this.app);
                int imageMaxHeight = LandmarkMapObject.getImageMaxHeight(this.app);
                Bitmap scaledBitmap = ResourceUtil.scale(bitmap, imageMaxWidth, imageMaxHeight);
                this.cache.put(imageName, scaledBitmap);
                
                Log.i("ImageDownloader", "readBitmapFromFile() bitmap read: " + imageName);
                return (useImageInList ? bitmap : scaledBitmap);
            }
            catch(FileNotFoundException e) {
                Log.i("ImageDownloader", "readBitmapFromFile() " + e);
            }
            finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                    in = null;
                }
            }
        }
        return null;
    }

    private synchronized String getNextImageName() {
        while (this.queue.isEmpty()) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }
        }

        return this.queue.get(0);
    }

    public interface ImageDownloadListener {
        void onImageDownloaded(Bitmap scaledBitmap, Bitmap origBitmap, String imageName, AndroidMapObject[] mapObjects);
    }

    private static class QueueItem {
        private String imageName;

        private Vector<ImageDownloadListener> listeners = new Vector<ImageDownloadListener>();
        private Vector<AndroidMapObject> mapObjects = new Vector<AndroidMapObject>();

        public QueueItem(String imageName) {
            this.imageName = imageName;
        }
        
        private void notifyListeners(Bitmap scaledBitmap, Bitmap origBitmap) {
            for (ImageDownloadListener listener : this.listeners) {
                listener.onImageDownloaded(scaledBitmap, origBitmap, this.imageName, this.getMapObjects());
            }
        }

        public void addListener(ImageDownloadListener listener) {
            if (!this.listeners.contains(listener)) {
                this.listeners.add(listener);
            }
        }

        public void addMapObject(AndroidMapObject mapObject) {
            if (mapObject != null && !this.mapObjects.contains(mapObject)) {
                this.mapObjects.add(mapObject);
            }
        }

        private AndroidMapObject[] getMapObjects() {
            return this.mapObjects
                    .toArray(new AndroidMapObject[this.mapObjects.size()]);
        }
    }
}
