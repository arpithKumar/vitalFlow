package com.vitalflow.app.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public class BitmapUtils {

    public static Bitmap decodeSampledBitmap(ContentResolver resolver, Uri uri, int reqWidth, int reqHeight) throws IOException {
        InputStream is = resolver.openInputStream(uri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);
        is.close();

        int inSampleSize = 1;
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            final int halfHeight = options.outHeight / 2;
            final int halfWidth = options.outWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth)
                inSampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        InputStream is2 = resolver.openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(is2, null, options);
        is2.close();
        return bitmap;
    }
}