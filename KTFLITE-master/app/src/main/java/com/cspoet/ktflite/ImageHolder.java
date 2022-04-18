package com.cspoet.ktflite;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ImageHolder extends RecyclerView.ViewHolder{
    ImageView iv;
    public ImageHolder(@NonNull View itemView) {
        super(itemView);
        iv=itemView.findViewById(R.id.iv);
    }

    public ImageView getIv() {
        return iv;
    }
}