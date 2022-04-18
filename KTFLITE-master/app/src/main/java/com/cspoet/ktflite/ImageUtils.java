package com.cspoet.ktflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Author   wildma
 * Github   https://github.com/wildma
 * Date     2018/6/24
 * Desc     ${图片相关工具类}
 */

public class ImageUtils {


    //将图片裁剪为正方形图片
    public static Bitmap centerSquareScaleBitmap(Bitmap bitmap, int edgeLength) {
        if (null == bitmap || edgeLength <= 0) {
            return null;
        }
        Bitmap result = bitmap;
        int widthOrg = bitmap.getWidth();
        int heightOrg = bitmap.getHeight();

        if (widthOrg > edgeLength && heightOrg > edgeLength) {
            //压缩到一个最小长度是edgeLength的bitmap
            int longerEdge = (int) (edgeLength * Math.max(widthOrg, heightOrg) / Math.min(widthOrg, heightOrg));
            int scaledWidth = widthOrg > heightOrg ? longerEdge : edgeLength;
            int scaledHeight = widthOrg > heightOrg ? edgeLength : longerEdge;
            Bitmap scaledBitmap;

            try {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
            } catch (Exception e) {
                return null;
            }

            //从图中截取正中间的正方形部分。
            int xTopLeft = (scaledWidth - edgeLength) / 2;
            int yTopLeft = (scaledHeight - edgeLength) / 2;

            try {
                result = Bitmap.createBitmap(scaledBitmap, xTopLeft, yTopLeft, edgeLength, edgeLength);
                scaledBitmap.recycle();
            } catch (Exception e) {
                return null;
            }
        }

        return result;
    }


    /**
     * 判断bitmap对象是否为空
     *
     * @param src 源图片
     * @return {@code true}: 是<br>{@code false}: 否
     */
    private static boolean isEmptyBitmap(Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    /**
     * 将byte[]转换成Bitmap
     *
     * @param bytes
     * @param width
     * @param height
     * @return
     */
    public static Bitmap getBitmapFromByte(byte[] bytes, int width, int height) {
        final YuvImage image = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length);
        if (!image.compressToJpeg(new Rect(0, 0, width, height), 100, os)) {
            return null;
        }
        byte[] tmp = os.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(tmp, 0, tmp.length);
        return bmp;
    }


    /**
     * 旋转bitmap
     *
     * @param b       将要旋转的图片
     * @param degrees 将要旋转的角度
     * @return
     */
    public static Bitmap rotateBmp(Bitmap b, int degrees) {
        if (degrees != 0 && b != null) {
            Matrix m = new Matrix();
            m.setRotate(degrees, (float) b.getWidth() / 2,
                    (float) b.getHeight() / 2);
            try {
                Bitmap b2 = Bitmap.createBitmap(b, 0, 0, b.getWidth(),
                        b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }
            } catch (OutOfMemoryError ex) {


            }
        }
        return b;
    }

    /**
     * 图片翻转
     *
     * @param bmp 原图
     *            return 翻转后的图像
     */
    public static Bitmap flipBmp(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1); // 镜像水平翻转
        Bitmap convertBmp = Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true);


        return convertBmp;
    }

    /**
     * 裁剪
     *
     * @param bitmap     原图
     * @param proportion 长是宽的几倍，只截取图片正中间的图像，传入为1时长等于宽
     * @return 裁剪后的图像
     */

    public static Bitmap cropBitmap(Bitmap bitmap, double proportion) {
        if (proportion > 0) {

            int w = bitmap.getWidth(); // 得到图片的宽，高
            int h = bitmap.getHeight();

            if (proportion > w || proportion > h) {
//                如果倍率太大，直接原图打回
                return bitmap;
            }

            int newh = (int) (w * proportion);

//            先判定是要裁剪width，还是裁剪height，还是都不裁
            //newh等于原来的h，说明原图大小不变，直接原封不动的把图返回回去就行
            if (newh == h) {
                return bitmap;
            }
            //newh小于原来的h，说明原图的高太大了，要缩小高度
            else if (newh < h) {
                return Bitmap.createBitmap(bitmap, 0, (h - newh) / 2, w, newh, null, false);
            }
            //newh大于原来的h，说明原图的宽太大了，要缩小宽度
            else if (newh > h) {
                //要缩小的宽度为原图的高除以proportion,这里加个判断,和原来的width做一下对比，取最小值
                int newW = Math.min((int) (h / proportion), w);
                if (newW == w) {
                    //如果裁剪后的宽不变，直接原路打回
                    return bitmap;
                } else {
                    //这里注意，因为上面已经取过最小值了，所以这里不需要比newW和w的大小了，newW只能比w小或者等于w
                    return Bitmap.createBitmap(bitmap, (w - newW) / 2, 0, newW, h, null, false);
                }
            } else {
                return bitmap;
            }
        } else {
            return bitmap;
        }
    }


    /**
     * 裁剪
     *
     * @param bitmap 原图
     * @param width  裁剪后的宽度
     * @param height 裁剪后的长度
     * @param from   裁剪部分，0为裁剪底部和右边,1为裁剪顶部和左边
     * @return 裁剪后的图像
     */

    public static Bitmap cropBitmap(Bitmap bitmap, int width, int height, int from) {


        if (height > bitmap.getHeight() || width > bitmap.getWidth()) {
            return bitmap;
        }


        switch (from) {
            case 0:
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, null, false);
                break;
            case 1:

                bitmap = Bitmap.createBitmap(bitmap, bitmap.getWidth() - width, bitmap.getHeight() - height, width, height, null, false);
                break;
        }
        return bitmap;
    }

    public static ArrayList<MatOfPoint> SortContours(@NotNull ArrayList<MatOfPoint> cents, @NotNull String method) {
        boolean reverse = false;
        int k = 0;

        if ("right-to-left".equals(method) || "bottom-to-top".equals(method)) {
            reverse = true;
            k = 1;
        }
        List<org.opencv.core.Rect> boundingBoxes = new ArrayList<org.opencv.core.Rect>();
        for (int i = 0; i < cents.size(); i++) {
            org.opencv.core.Rect rect = Imgproc.boundingRect(cents.get(i));
            boundingBoxes.add(rect);
            Log.e("hehe", "SortContours: "+rect.height+"//"+rect.width+"//"+rect.x+"//"+rect.y );
        }



//                (cnts, boundingBoxes) = zip( * sorted(zip(cnts, boundingBoxes),
//                key = lambda b:b[1][i], reverse = reverse))

        return cents;
    }

    //将assets下的文件转化为缓存，并获取缓存路径
    public static String getAssetsCacheFile(Context context, String fileName)   {
        File cacheFile = new File(context.getCacheDir(), fileName);
        try {
            InputStream inputStream = context.getAssets().open(fileName);
            try {
                FileOutputStream outputStream = new FileOutputStream(cacheFile);
                try {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buf)) > 0) {
                        outputStream.write(buf, 0, len);
                    }
                } finally {
                    outputStream.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cacheFile.getAbsolutePath();
    }

}
