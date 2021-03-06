/*
Copyright 2015 shizhefei（LuckyJayce）

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package jujube.android.widgets.largeimage;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ScrollerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import jujube.android.widgets.largeimage.factory.BitmapDecoderFactory;
import jujube.android.widgets.largeimage.factory.SimplePath;

/**
 * Created by LuckyJayce on 2016/11/24.
 */

public class LargeImageView extends View implements BlockImageLoader.OnImageLoadListener, ILargeImageView {
    private static final String tag = "largeImageView";

    private List<SimplePath> simplePaths = new ArrayList<>();
    private String name;

    private final GestureDetector gestureDetector;
    private final ScrollerCompat mScroller;
    private final BlockImageLoader imageBlockLoader;
    private final int mMinimumVelocity;
    private final int mMaximumVelocity;
    private final ScaleGestureDetector scaleGestureDetector;
    private int mDrawableWidth;
    private int mDrawableHeight;
    private float mScale = 1;
    private BitmapDecoderFactory mFactory;
    private float fitScale;
    private float maxScale;
    private float minScale;
    private BlockImageLoader.OnImageLoadListener mOnImageLoadListener;
    private Drawable mDrawable;
    private int mLevel;
    private ScaleHelper scaleHelper;
    private AccelerateInterpolator accelerateInterpolator;
    private DecelerateInterpolator decelerateInterpolator;
    private boolean isAttachedWindow;

    private Bitmap pathBitmap;
    private Canvas pathCanvas;

    public LargeImageView(Context context) {
        this(context, null);
    }

    public LargeImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LargeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mScroller = ScrollerCompat.create(getContext(), null);
        scaleHelper = new ScaleHelper();
        setFocusable(true);
        setWillNotDraw(false);
        gestureDetector = new GestureDetector(context, simpleOnGestureListener);
        scaleGestureDetector = new ScaleGestureDetector(context, onScaleGestureListener);

        imageBlockLoader = new BlockImageLoader(context);
        imageBlockLoader.setOnImageLoadListener(this);
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scaleHelper.computeScrollOffset()) {
            setScale(scaleHelper.getCurScale(), scaleHelper.getStartX(), scaleHelper.getStartY());
        }
        if (mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            if (oldX != x || oldY != y) {
                final int rangeY = getScrollRangeY();
                final int rangeX = getScrollRangeX();
                overScrollByCompat(x - oldX, y - oldY, oldX, oldY, rangeX, rangeY,
                        0, 0, false);
            }
            if (!mScroller.isFinished()) {
                notifyInvalidate();
            }
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (direction > 0) {
            return getScrollX() < getScrollRangeX();
        } else {
            return getScrollX() > 0 && getScrollRangeX() > 0;
        }
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (direction > 0) {
            return getScrollY() < getScrollRangeY();
        } else {
            return getScrollY() > 0 && getScrollRangeY() > 0;
        }
    }

    @Override
    public void setOnImageLoadListener(BlockImageLoader.OnImageLoadListener onImageLoadListener) {
        this.mOnImageLoadListener = onImageLoadListener;
    }

    @Override
    public BlockImageLoader.OnImageLoadListener getOnImageLoadListener() {
        return mOnImageLoadListener;
    }

    /**
     * Sets a Bitmap as the content of this ImageView.
     *
     * @param bm The bitmap to initFitImageScale
     */
    @Override
    public void setImage(Bitmap bm) {
        setImageDrawable(new BitmapDrawable(getResources(), bm));
    }

    @Override
    public void setImage(Drawable drawable) {
        setImageDrawable(drawable);
    }

    @Override
    public void setImage(@DrawableRes int resId) {
        setImageDrawable(ContextCompat.getDrawable(getContext(), resId));
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        mFactory = null;
        mScale = 1.0f;
        scrollTo(0, 0);
        if (mDrawable != drawable) {
            final int oldWidth = mDrawableWidth;
            final int oldHeight = mDrawableHeight;
            updateDrawable(drawable);
            onLoadImageSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            if (oldWidth != mDrawableWidth || oldHeight != mDrawableHeight) {
                requestLayout();
            }
            notifyInvalidate();
        }
    }

    @Override
    public void setImage(BitmapDecoderFactory factory) {
        setImage(factory, null);
    }

    @Override
    public void setImage(BitmapDecoderFactory factory, Drawable defaultDrawable) {
        mScale = 1.0f;
        this.mFactory = factory;
        scrollTo(0, 0);
        if (defaultDrawable != null) {
            onLoadImageSize(defaultDrawable.getIntrinsicWidth(), defaultDrawable.getIntrinsicHeight());
        }
        imageBlockLoader.setBitmapDecoderFactory(factory);
        invalidate();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private void updateDrawable(Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
            unscheduleDrawable(mDrawable);
            if (isAttachedWindow) {
                mDrawable.setVisible(false, false);
            }
        }
        mDrawable = d;

        if (d != null) {
            d.setCallback(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                d.setLayoutDirection(getLayoutDirection());
            }
            if (d.isStateful()) {
                d.setState(getDrawableState());
            }
            if (isAttachedWindow) {
                d.setVisible(getWindowVisibility() == VISIBLE && isShown(), true);
            }
            d.setLevel(mLevel);
            mDrawableWidth = d.getIntrinsicWidth();
            mDrawableHeight = d.getIntrinsicHeight();
//            applyImageTint();
//            applyColorMod();
//
//            configureBounds();
        } else {
            mDrawableWidth = mDrawableHeight = -1;
        }
    }

    @Override
    public boolean hasLoad() {
        if (mDrawable != null) {
            return true;
        } else if (mFactory != null) {
            return imageBlockLoader.hasLoad();
        }
        return false;
    }

    @Override
    public int computeVerticalScrollRange() {
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        int scrollRange = getContentHeight();
        final int scrollY = getScrollY();
        final int overscrollBottom = Math.max(0, scrollRange - contentHeight);
        if (scrollY < 0) {
            scrollRange -= scrollY;
        } else if (scrollY > overscrollBottom) {
            scrollRange += scrollY - overscrollBottom;
        }
        return scrollRange;
    }

    @Override
    public int getImageWidth() {
        if (mDrawable != null) {
            return mDrawableWidth;
        } else if (mFactory != null) {
            if (hasLoad()) {
                return mDrawableWidth;
            }
        }
        return 0;
    }

    @Override
    public int getImageHeight() {
        if (mDrawable != null) {
            return mDrawableHeight;
        } else if (mFactory != null) {
            if (hasLoad()) {
                return mDrawableHeight;
            }
        }
        return 0;
    }

    private int getScrollRangeY() {
        final int contentHeight = getHeight() - getPaddingBottom() - getPaddingTop();
        return getContentHeight() - contentHeight;
    }

    /**
     *
     */
    @Override
    public int computeVerticalScrollOffset() {
        return Math.max(0, super.computeVerticalScrollOffset());
    }

    /**
     *
     */
    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    /**
     *
     */
    @Override
    public int computeHorizontalScrollRange() {
        final int contentWidth = getWidth() - getPaddingRight() - getPaddingLeft();
        int scrollRange = getContentWidth();
        final int scrollX = getScrollX();
        final int overscrollRight = Math.max(0, scrollRange - contentWidth);
        if (scrollX < 0) {
            scrollRange -= scrollX;
        } else if (scrollX > overscrollRight) {
            scrollRange += scrollX - overscrollRight;
        }
        return scrollRange;
    }

    private int getScrollRangeX() {
        final int contentWidth = getWidth() - getPaddingRight() - getPaddingLeft();
        return (getContentWidth() - contentWidth);
    }

    private int getContentWidth() {
        if (hasLoad()) {
            return (int) (getMeasuredWidth() * mScale);
        }
        return 0;
    }

    private int getContentHeight() {
        if (hasLoad()) {
            if (getImageWidth() == 0) {
                return 0;
            }
            return (int) (1.0f * getMeasuredWidth() * getImageHeight() / getImageWidth() * mScale);
        }
        return 0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (pathBitmap != null)
            pathBitmap.recycle();
        pathBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        pathCanvas = new Canvas(pathBitmap);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
//        if (pathBitmap != null && pathCanvas != null) {
//            pathBitmap.recycle();
//            pathBitmap = Bitmap.createBitmap(getMeasuredHeight(), getMeasuredWidth(), Bitmap.Config.ARGB_8888);
//            pathCanvas = new Canvas(pathBitmap);
//        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
            return;
        }
        int drawOffsetX = 0;
        int drawOffsetY = 0;
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        int layoutWidth = getMeasuredWidth();
        int layoutHeight = getMeasuredHeight();
        if (layoutWidth > contentWidth) {
            drawOffsetX = (layoutWidth - contentWidth) / 2;
        }
        if (layoutHeight > contentHeight) {
            drawOffsetY = (layoutHeight - contentHeight) / 2;
        }
        if (mDrawable != null) {
            mDrawable.setBounds(drawOffsetX, drawOffsetY, drawOffsetX + contentWidth, drawOffsetY + contentHeight);
            mDrawable.draw(canvas);
        } else if (mFactory != null) {
            int mOffsetX = 0;
            int mOffsetY = 0;
            int left = getScrollX();
            int right = left + getMeasuredWidth();
            int top = getScrollY();
            int bottom = top + getMeasuredHeight();
            float width = mScale * getWidth();
            float imgWidth = imageBlockLoader.getWidth();

            float imageScale = imgWidth / width;

            // 需要显示的图片的实际宽度。
            imageRect.left = (int) Math.ceil((left - mOffsetX) * imageScale);
            imageRect.top = (int) Math.ceil((top - mOffsetY) * imageScale);
            imageRect.right = (int) Math.ceil((right - mOffsetX) * imageScale);
            imageRect.bottom = (int) Math.ceil((bottom - mOffsetY) * imageScale);

            List<BlockImageLoader.DrawData> drawData = imageBlockLoader.loadImageBlocks(imageScale, imageRect);

            int saveCount = canvas.save();
            for (BlockImageLoader.DrawData data : drawData) {
                if (mOnImageLoadListener != null) {
                    mOnImageLoadListener.onUIVisiable();
                }
                Rect drawRect = data.imageRect;
                drawRect.left = (int) (Math.ceil(drawRect.left / imageScale) + mOffsetX) + drawOffsetX;
                drawRect.top = (int) (Math.ceil(drawRect.top / imageScale) + mOffsetY) + drawOffsetY;
                drawRect.right = (int) (Math.ceil(drawRect.right / imageScale) + mOffsetX) + drawOffsetX;
                drawRect.bottom = (int) (Math.ceil(drawRect.bottom / imageScale) + mOffsetY) + drawOffsetY;
                canvas.drawBitmap(data.bitmap, data.srcRect, drawRect, null);
            }

            if (hasLoad()) {
                pathCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                for(SimplePath simplePath : simplePaths) {
                    drawPath.rewind();
                    List<Point> points = simplePath.points;
                    for(int i = 0; i < points.size(); i ++) {
                        deComputerPoint(points.get(i).x, points.get(i).y);
                        if(i == 0) {
//                            drawPath.moveTo(targetPoint.x, targetPoint.y);
                            drawPath.moveTo(targetPoint.x - getScrollX(), targetPoint.y - getScrollY());
                        }else {
//                            drawPath.lineTo(targetPoint.x, targetPoint.y);
                            drawPath.lineTo(targetPoint.x - getScrollX(), targetPoint.y - getScrollY());
                        }
                    }
                    pathCanvas.drawPath(drawPath, simplePath.paint);
//                    canvas.drawPath(drawPath, simplePath.paint);

                }
                canvas.drawBitmap(pathBitmap, getScrollX(), getScrollY(), null);
            }

            canvas.restoreToCount(saveCount);

        }

    }

    private Path drawPath = new Path();
    private Point targetPoint = new Point(0, 0);
    private Rect imageRect = new Rect();

    @Override
    public void onUIVisiable() {

    }

    @Override
    public void onBlockImageLoadFinished() {
        notifyInvalidate();
        if (mOnImageLoadListener != null) {
            mOnImageLoadListener.onBlockImageLoadFinished();
        }
    }

    @Override
    public void onLoadImageSize(final int imageWidth, final int imageHeight) {
        mDrawableWidth = imageWidth;
        mDrawableHeight = imageHeight;
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();
        if (layoutWidth == 0 || layoutHeight == 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    initFitImageScale(imageWidth, imageHeight);
                }
            });
        } else {
            initFitImageScale(imageWidth, imageHeight);
        }
        notifyInvalidate();
        if (mOnImageLoadListener != null) {
            mOnImageLoadListener.onLoadImageSize(imageWidth, imageHeight);
        }
    }

    @Override
    public void onLoadFail(Exception e) {
        if (mOnImageLoadListener != null) {
            mOnImageLoadListener.onLoadFail(e);
        }
    }

    /**
     * 设置合适的缩放大小
     *
     * @param imageWidth
     * @param imageHeight
     */
    private void initFitImageScale(int imageWidth, int imageHeight) {
        final int layoutWidth = getMeasuredWidth();
        final int layoutHeight = getMeasuredHeight();
        if (imageWidth > imageHeight) {
            fitScale = (1.0f * imageWidth / layoutWidth) * layoutHeight / imageHeight;
            maxScale = 1.0f * imageWidth / layoutWidth * 4;
            minScale = 1.0f * imageWidth / layoutWidth / 4;
            if (minScale > 1) {
                minScale = 1;
            }
        } else {
            fitScale = 1.0f;
            minScale = 0.25f;
            maxScale = 1.0f * imageWidth / layoutWidth;
            float a = (1.0f * imageWidth / layoutWidth) * layoutHeight / imageHeight;
            float density = getContext().getResources().getDisplayMetrics().density;
            maxScale = maxScale * density;
            if (maxScale < 4) {
                maxScale = 4;
            }
            if (minScale > a) {
                minScale = a;
            }
        }
        if (criticalScaleValueHook != null) {
            minScale = criticalScaleValueHook.getMinScale(this, imageWidth, imageHeight, minScale);
            maxScale = criticalScaleValueHook.getMaxScale(this, imageWidth, imageHeight, maxScale);
        }
    }

    private void notifyInvalidate() {
        ViewCompat.postInvalidateOnAnimation(LargeImageView.this);
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedWindow = false;
        if (mDrawable != null) {
            mDrawable.setVisible(getVisibility() == VISIBLE, false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedWindow = true;
        imageBlockLoader.quit();
        if (mDrawable != null) {
            mDrawable.setVisible(false, false);
        }
    }

    private boolean overScrollByCompat(int deltaX, int deltaY,
                                       int scrollX, int scrollY,
                                       int scrollRangeX, int scrollRangeY,
                                       int maxOverScrollX, int maxOverScrollY,
                                       boolean isTouchEvent) {
        int oldScrollX = getScrollX();
        int oldScrollY = getScrollY();

        int newScrollX = scrollX;

        newScrollX += deltaX;

        int newScrollY = scrollY;

        newScrollY += deltaY;

        // Clamp values if at the limits and record
        final int left = -maxOverScrollX;
        final int right = maxOverScrollX + scrollRangeX;
        final int top = -maxOverScrollY;
        final int bottom = maxOverScrollY + scrollRangeY;

        boolean clampedX = false;
        if (newScrollX > right) {
            newScrollX = right;
            clampedX = true;
        } else if (newScrollX < left) {
            newScrollX = left;
            clampedX = true;
        }

        boolean clampedY = false;
        if (newScrollY > bottom) {
            newScrollY = bottom;
            clampedY = true;
        } else if (newScrollY < top) {
            newScrollY = top;
            clampedY = true;
        }

        if (newScrollX < 0) {
            newScrollX = 0;
        }
        if (newScrollY < 0) {
            newScrollY = 0;
        }
        onOverScrolled(newScrollX, newScrollY, clampedX, clampedY);
        return getScrollX() - oldScrollX == deltaX || getScrollY() - oldScrollY == deltaY;
    }

    private boolean fling(int velocityX, int velocityY) {
        if (Math.abs(velocityX) < mMinimumVelocity) {
            velocityX = 0;
        }
        if (Math.abs(velocityY) < mMinimumVelocity) {
            velocityY = 0;
        }
        final int scrollY = getScrollY();
        final int scrollX = getScrollX();
        final boolean canFlingX = (scrollX > 0 || velocityX > 0) &&
                (scrollX < getScrollRangeX() || velocityX < 0);
        final boolean canFlingY = (scrollY > 0 || velocityY > 0) &&
                (scrollY < getScrollRangeY() || velocityY < 0);
        boolean canFling = canFlingY || canFlingX;
        if (canFling) {
            velocityX = Math.max(-mMaximumVelocity, Math.min(velocityX, mMaximumVelocity));
            velocityY = Math.max(-mMaximumVelocity, Math.min(velocityY, mMaximumVelocity));
            int height = getHeight() - getPaddingBottom() - getPaddingTop();
            int width = getWidth() - getPaddingRight() - getPaddingLeft();
            int bottom = getContentHeight();
            int right = getContentWidth();
            mScroller.fling(getScrollX(), getScrollY(), velocityX, velocityY, 0, Math.max(0, right - width), 0,
                    Math.max(0, bottom - height), width / 2, height / 2);
            notifyInvalidate();
            return true;
        }
        return false;
    }

    protected void onOverScrolled(int scrollX, int scrollY,
                                  boolean clampedX, boolean clampedY) {
        super.scrollTo(scrollX, scrollY);
    }

    /**
     * 解决横竖屏切换时第一次加载线条移位的问题，第一次加载不需要计算fraction
     */
    private boolean orientationChanged = false;
    private Activity activity;

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (hasLoad() && oldw > 0) {
            if(simplePaths.size() > 0) {
                if (!    orientationChanged) {
                    for (SimplePath path : simplePaths) {
                        for (Point point : path.points) {
                            float fraction = (float) w / (float) oldw;
                            point.x  = point.x * fraction;
                            point.y = point.y * fraction;
                        }
                    }
                }
            }
            if (orientationChanged) {
                orientationChanged = false;
                if (activity != null) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
            initFitImageScale(mDrawableWidth, mDrawableHeight);
        }
    }

    private OnClickListener onClickListener;
    private OnLongClickListener onLongClickListener;
    private OnSingleTapListener onSingleTapListener;

    public void setOnSingleTapListener(OnSingleTapListener l) {
        this.onSingleTapListener = l;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        super.setOnClickListener(l);
        this.onClickListener = l;
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        super.setOnLongClickListener(l);
        this.onLongClickListener = l;
    }

    public void setCriticalScaleValueHook(CriticalScaleValueHook criticalScaleValueHook) {
        this.criticalScaleValueHook = criticalScaleValueHook;
    }

    private CriticalScaleValueHook criticalScaleValueHook;

    /**
     * Hook临界值
     */
    public interface CriticalScaleValueHook {

        /**
         * 返回最小的缩放倍数
         * scale为1的话表示，显示的图片和View一样宽
         *
         * @param largeImageView
         * @param imageWidth
         * @param imageHeight
         * @param suggestMinScale 默认建议的最小的缩放倍数
         * @return
         */
        float getMinScale(LargeImageView largeImageView, int imageWidth, int imageHeight, float suggestMinScale);

        /**
         * 返回最大的缩放倍数
         * scale为1的话表示，显示的图片和View一样宽
         *
         * @param largeImageView
         * @param imageWidth
         * @param imageHeight
         * @param suggestMaxScale 默认建议的最大的缩放倍数
         * @return
         */
        float getMaxScale(LargeImageView largeImageView, int imageWidth, int imageHeight, float suggestMaxScale);

    }

    private GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            if (!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {

            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (onClickListener != null && isClickable()) {
                onClickListener.onClick(LargeImageView.this);
            }
            if (onSingleTapListener != null) {
                onSingleTapListener.onSingleTap();
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isEnabled()) {
                return false;
            }
            overScrollByCompat((int) distanceX, (int) distanceY, getScrollX(), getScrollY(), getScrollRangeX(), getScrollRangeY(), 0, 0, false);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (onLongClickListener != null && isLongClickable()) {
                onLongClickListener.onLongClick(LargeImageView.this);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!isEnabled()) {
                return false;
            }
            fling((int) -velocityX, (int) -velocityY);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (!hasLoad()) {
                return false;
            }
            float newScale;
            if (mScale < 1) {
                newScale = 1;
            } else if (mScale < fitScale && fitScale < maxScale) {
                newScale = fitScale;
            } else if (mScale < maxScale / 2 && mScale < 1.5f) {
                if (maxScale / 2 < 1.5f) {
                    newScale = 1.5f;
                } else {
                    newScale = maxScale / 2;
                }
            } else if (mScale < maxScale) {
                newScale = maxScale;
            } else {
                newScale = 1;
            }
            smoothScale(newScale, (int) e.getX(), (int) e.getY());
            return true;
        }
    };


    public void smoothScale(float newScale, int centerX, int centerY) {
        if (mScale > newScale) {
            if (accelerateInterpolator == null) {
                accelerateInterpolator = new AccelerateInterpolator();
            }
            scaleHelper.startScale(mScale, newScale, centerX, centerY, accelerateInterpolator);
        } else {
            if (decelerateInterpolator == null) {
                decelerateInterpolator = new DecelerateInterpolator();
            }
            scaleHelper.startScale(mScale, newScale, centerX, centerY, decelerateInterpolator);
        }
        notifyInvalidate();
    }

    private ScaleGestureDetector.OnScaleGestureListener onScaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!isEnabled()) {
                return false;
            }
            if (!hasLoad()) {
                return false;
            }
            float newScale;
            newScale = mScale * detector.getScaleFactor();
            if (newScale > maxScale) {
                newScale = maxScale;
            } else if (newScale < minScale) {
                newScale = minScale;
            }
            setScale(newScale, (int) detector.getFocusX(), (int) detector.getFocusY());
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        }
    };


    @Override
    public void setScale(float scale) {
        setScale(scale, getMeasuredWidth() >> 1, getMeasuredHeight() >> 1);
    }

    @Override
    public float getScale() {
        return mScale;
    }

    public void setScale(float scale, int centerX, int centerY) {
        if (!hasLoad()) {
            return;
        }
        float preScale = mScale;
        mScale = scale;
        int sX = getScrollX();
        int sY = getScrollY();
        int dx = (int) ((sX + centerX) * (scale / preScale - 1));
        int dy = (int) ((sY + centerY) * (scale / preScale - 1));
        overScrollByCompat(dx, dy, sX, sY, getScrollRangeX(), getScrollRangeY(), 0, 0, false);
        notifyInvalidate();
    }

    public void startDraw(float x, float y, Paint paint) {
        List<Point> points = new ArrayList<>();
        points.add(computePoint(x, y));
        simplePaths.add(new SimplePath(points, paint));
        notifyInvalidate();
    }

    public void draw(float x, float y) {
        List<Point> points = simplePaths.get(simplePaths.size() - 1).points;
        points.add(computePoint(x, y));
        notifyInvalidate();
    }

    public void undo() {
        if(simplePaths.size() > 0) {
            simplePaths.remove(simplePaths.size() - 1);
            notifyInvalidate();
        }
    }

    public void clearAll() {
        simplePaths.clear();
        notifyInvalidate();
    }

    public String saveDrawPaths() {
        StringBuilder str = new StringBuilder();
        str.append("[");
        for (SimplePath sp : simplePaths) {
            str.append("{color:");
            str.append(sp.paint.getColor());
            str.append(",");
            str.append("size:");
            str.append(sp.paint.getStrokeWidth());
            str.append(",");
            str.append("points:[");
            for (Point p : sp.points) {
                str.append((int)p.x);
                str.append(",");
                str.append((int)p.y);
                str.append(",");
            }
            str.deleteCharAt(str.lastIndexOf(","));
            str.append("]},");
        }
        if (str.length() > 1 && str.indexOf(",") != -1)
            str.deleteCharAt(str.lastIndexOf(","));
        str.append("]");
        return str.toString();
    }

    public void orientationChanged() {
        orientationChanged = true;
    }

    public boolean isLandScape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public void restoreDrawPaths(String content) {
        simplePaths.clear();
        try {
            JSONArray paths = new JSONArray(content);
            for (int i = 0; i < paths.length(); ++i) {
                JSONObject path = paths.getJSONObject(i);
                Paint brush = new Paint();
                brush.setAntiAlias(true);
                brush.setStyle(Paint.Style.STROKE);
                brush.setStrokeJoin(Paint.Join.ROUND);
                brush.setColor(path.getInt("color"));
                brush.setStrokeWidth(path.getInt("size"));
                ArrayList<Point> points = new ArrayList<>();
                JSONArray pointArray = path.getJSONArray("points");
                for (int j = 0; j < pointArray.length(); j = j + 2) {
                    points.add(new Point((float) pointArray.getInt(j), (float) pointArray.getInt(j+1)));
                }
                simplePaths.add(new SimplePath(points, brush));

            }
        }catch (JSONException e) {
            e.printStackTrace();
        }
        notifyInvalidate();
    }

    private Point computePoint(float x, float y) {
        float px = x + getScrollX();
        float py = y + getScrollY();
        if(getMeasuredWidth() > getContentWidth()) {
            px = px - (getMeasuredWidth() - getContentWidth()) / 2;
        }
        if(getMeasuredHeight() > getContentHeight()) {
            py = py - (getMeasuredHeight() - getContentHeight()) / 2;
        }
        return new Point(px / mScale, py / mScale);
    }

    private Point deComputerPoint(float x, float y) {
        float px = x * mScale;
        float py = y * mScale;
        if(getMeasuredWidth() > getContentWidth()) {
            px = px + (getMeasuredWidth() - getContentWidth()) / 2;
        }
        if(getMeasuredHeight() > getContentHeight()) {
            py = py + (getMeasuredHeight() - getContentHeight()) / 2;
        }
        targetPoint.x = px;
        targetPoint.y = py;
        return targetPoint;
    }

    public interface OnSingleTapListener {

        void onSingleTap();

    }
}