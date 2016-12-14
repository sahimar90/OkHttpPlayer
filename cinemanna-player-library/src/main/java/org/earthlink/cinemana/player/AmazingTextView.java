package org.earthlink.cinemana.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.TextView;


/**
 * Created by ucsunup on 16-9-26.
 */
public class AmazingTextView extends TextView {
    private Context mContext;
    private float mRadius = 10f;



    private int mBackGroundColor = 0X0d0d0d;

    private float mHeight;
    private float mWidth;

    public AmazingTextView(Context paramContext) {
        super(paramContext);
        this.mContext = paramContext;

    }

    public AmazingTextView(Context paramContext, AttributeSet paramAttributeSet) {
        super(paramContext, paramAttributeSet);
        this.mContext = paramContext;
        getParameter(paramAttributeSet);
    }

    public AmazingTextView(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
        this.mContext = paramContext;
        getParameter(paramAttributeSet);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AmazingTextView(Context paramContext, AttributeSet paramAttributeSet, int paramInt1, int paramInt2) {
        super(paramContext, paramAttributeSet, paramInt1, paramInt2);
        this.mContext = paramContext;
        getParameter(paramAttributeSet);
    }

    public float getRadius() {
        return this.mRadius;
    }

    public void setRadius(int radius) {
        this.mRadius = radius;
    }

    public int getmBackGroundColor() {
        return mBackGroundColor;
    }

    public void setmBackGroundColor(int mBackGroundColor) {
        this.mBackGroundColor = mBackGroundColor;
    }

    private void getParameter(AttributeSet paramAttributeSet) {
        // init bg as gradient
        setBackgroundResource(R.drawable.recstyletext);

        TypedArray a = mContext.obtainStyledAttributes(paramAttributeSet, R.styleable.AmazingTextView);
        this.mRadius = a.getDimension(R.styleable.AmazingTextView_radius, mRadius);
        this.mBackGroundColor = a.getColor(R.styleable.AmazingTextView_backgroundColor, mBackGroundColor);
        a.recycle();

        initView();
    }

    private void initView() {
        GradientDrawable gd = (GradientDrawable) getBackground();
        mRadius = mRadius < 0 ? 0 : mRadius;
        gd.setCornerRadius(mRadius);
        gd.setColor(mBackGroundColor);
    }
}