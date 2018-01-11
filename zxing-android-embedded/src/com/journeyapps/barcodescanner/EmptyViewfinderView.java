package com.journeyapps.barcodescanner;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import com.journeyapps.barcodescanner.ViewfinderView;

public class EmptyViewfinderView extends ViewfinderView {

    public EmptyViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onDraw(Canvas canvas) {
    }
}
