package com.screenguard.timer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 提醒图片存储：用户从相册选的图复制到应用私有目录。
 * 在 App 里换图即可，不必重新编译。
 */
public final class ReminderImageStore {

    private static final String FILE_NAME = "reminder_image.jpg";

    private ReminderImageStore() {
    }

    public static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean exists(Context context) {
        return file(context).exists();
    }

    public static boolean save(Context context, Uri sourceUri) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(file(context))) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(Context context) {
        File f = file(context);
        if (f.exists()) f.delete();
    }

    /** 解码并限制尺寸，防止大图占满内存 */
    public static Bitmap load(Context context) {
        File f = file(context);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            int sample = 1;
            int target = 1400;
            while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }
}
