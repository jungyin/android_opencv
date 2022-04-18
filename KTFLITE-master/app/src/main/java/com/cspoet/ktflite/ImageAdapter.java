package com.cspoet.ktflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.nio.FloatBuffer;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageHolder> {
    @NonNull
    @Override
    public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ImageHolder(LayoutInflater.from(context).inflate(R.layout.item_iv,parent,false)) {
            @Override
            public String toString() {
                return super.toString();
            }
        };
    }

    @Override
    public void onBindViewHolder(@NonNull ImageHolder holder, int position) {

        float[]f={0,0};


        Bitmap bitmap = Bitmap.createBitmap(images.get(position).width(), images.get(position).height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(images.get(position),bitmap);
        holder.iv.setImageBitmap(bitmap);

    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    Context context;
    List<Mat> images;

    public ImageAdapter(Context context, List<Mat> images) {
        this.context = context;
        this.images = images;
    }


}
