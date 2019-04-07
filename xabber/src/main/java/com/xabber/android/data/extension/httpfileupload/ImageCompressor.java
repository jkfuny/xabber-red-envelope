package com.xabber.android.data.extension.httpfileupload;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageCompressor {

    private static final int IMAGE_QUALITY = 90;
    private static final int MAX_SIZE_PIXELS = 1280;

    public static Bitmap decodeFile(File file, int width, int height) {
        try {
            // decode image size
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(file),null, options);

            // the new size we want to scale to
            final int REQUIRED_WIDTH = width;
            final int REQUIRED_HIGHT = height;

            // find the correct scale value. It should be the power of 2.
            int scale = 1;
            while (options.outWidth / scale / 2 >= REQUIRED_WIDTH
                    && options.outHeight / scale / 2 >= REQUIRED_HIGHT)
                scale *= 2;

            // decode with inSampleSize
            BitmapFactory.Options newOptions = new BitmapFactory.Options();
            newOptions.inSampleSize = scale;
            return BitmapFactory.decodeStream(new FileInputStream(file), null, newOptions);

        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static File compressImage(final File file, String outputDirectory) {
        String path = file.getPath();
        String format = path.substring(path.lastIndexOf(".")).substring(1);
        Bitmap source;
        try {
            source = decodeFile(file, MAX_SIZE_PIXELS, MAX_SIZE_PIXELS);
        } catch (Exception e) {
            Log.d(ImageCompressor.class.toString(), e.toString());
            return null;
        }

        Bitmap.CompressFormat compressFormat;

        // if png pr webp have allowed resolution then not compress it
        if ("png".equals(format) || "webp".equals(format)) return file;

        // select format
        switch (format) {
            case "png":
                compressFormat = Bitmap.CompressFormat.PNG;
                break;
            case "webp":
                compressFormat = Bitmap.CompressFormat.WEBP;
                break;
            case "gif":
                return file;
            default:
                compressFormat = Bitmap.CompressFormat.JPEG;
        }

        // resize image
        Bitmap resizedBmp;
        if (source.getHeight() > MAX_SIZE_PIXELS || source.getWidth() > MAX_SIZE_PIXELS) {
            resizedBmp = resizeBitmap(source, MAX_SIZE_PIXELS);
        } else  {
            resizedBmp = source;
        }

        // create directory if not exist
        File directory = new File(outputDirectory);
        directory.mkdirs();

        // compress image
        File result = new File(outputDirectory, file.getName());
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(result);
            resizedBmp.compress(compressFormat, IMAGE_QUALITY, fOut);
            fOut.flush();
            fOut.close();
            source.recycle();
            resizedBmp.recycle();

        } catch (Exception e) {
            return null;
        }

        // copy EXIF orientation from original image
        try {
            ExifInterface oldExif = new ExifInterface(file.getPath());
            String exifOrientation = oldExif.getAttribute(ExifInterface.TAG_ORIENTATION);
            if (exifOrientation != null) {
                ExifInterface newExif = new ExifInterface(result.getPath());
                newExif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation);
                newExif.saveAttributes();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static Bitmap resizeBitmap(Bitmap source, int maxSizePixels) {
        int targetWidth, targetHeight;
        double aspectRatio;

        if (source.getWidth() > source.getHeight()) {
            targetWidth = maxSizePixels;
            aspectRatio = (double) source.getHeight() / (double) source.getWidth();
            targetHeight = (int) (targetWidth * aspectRatio);
        } else {
            targetHeight = maxSizePixels;
            aspectRatio = (double) source.getWidth() / (double) source.getHeight();
            targetWidth = (int) (targetHeight * aspectRatio);
        }

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false);
    }

    private Bitmap getBitmap(String srcPath) {
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        // 开始是先把newOpts.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath,newOpts); // 此时返回bitmap为null

        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        // 以800*480分辨率为例
        float hh = 800f;  // 这里设置高度为800f
        float ww = 480f;  // 这里设置宽度为480f
        // 缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int scale = 1;  // be=1表示不缩放
        if (w > h && w > ww) {  // 如果宽度大的话根据宽度固定大小缩放
            scale =  (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) { // 如果高度高的话根据宽度固定大小缩放
            scale = (int) (newOpts.outHeight / hh);
        } if (scale <= 0) scale = 1; newOpts.inSampleSize = scale; // 设置缩放比例 // 重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        bitmap = BitmapFactory.decodeFile(srcPath, newOpts); return bitmap;
    }


}
