
package com.cooeeui.brand.zenlauncher.scenes;

import java.util.ArrayList;
import java.util.HashSet;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import com.cooeeui.brand.zenlauncher.Launcher;
import com.cooeeui.brand.zenlauncher.LauncherAppState;
import com.cooeeui.brand.zenlauncher.LauncherModel;
import com.cooeeui.brand.zenlauncher.R;
import com.cooeeui.brand.zenlauncher.apps.AppInfo;
import com.cooeeui.brand.zenlauncher.apps.IconCache;
import com.cooeeui.brand.zenlauncher.apps.ShortcutInfo;
import com.cooeeui.brand.zenlauncher.config.IconConfig;
import com.cooeeui.brand.zenlauncher.scenes.ui.BubbleView;
import com.cooeeui.brand.zenlauncher.scenes.utils.BitmapUtils;
import com.cooeeui.brand.zenlauncher.scenes.utils.DragController;
import com.cooeeui.brand.zenlauncher.scenes.utils.DragSource;

public class SpeedDial extends FrameLayout implements DragSource, View.OnTouchListener {

    private Launcher mLauncher;
    private DragController mDragController;
    private IconCache mIconCache;

    private static final int SPEED_DIAL_STATE_NORMAL = 0;
    private static final int SPEED_DIAL_STATE_DRAG = 1;
    private int mState = SPEED_DIAL_STATE_NORMAL;

    private int mIconSize;
    private int mPadding;
    private float[] mMidPoint = new float[2];
    private int mSize;

    private static final int BUBBLE_VIEW_CAPACITY = 9;
    private ArrayList<BubbleView> mBubbleViews = new ArrayList<BubbleView>(BUBBLE_VIEW_CAPACITY);

    public static final int EDIT_VIEW_ICON = 0x100;
    public static final int EDIT_VIEW_CHANGE = 0x101;
    public static final int EDIT_VIEW_DELETE = 0x102;

    private float mSelectX;
    private float mSelectY;
    private BubbleView mSelect;
    private View mSearchBar;
    private View mEditBottomView;

    private Bitmap mDefaultIcon;

    private static final int IN_DURATION = 300;
    private static final int OUT_DURATION = 200;
    private ValueAnimator mAnimatorSearch;
    private ValueAnimator mAnimatorEdit;
    private float mAnimatorValue;
    private Interpolator mDecelerate;
    private Interpolator mAccelerate;

    private static final int ALPHA_DURATION = 200;
    private BubbleView mAlphaView;
    private ValueAnimator mAlphaAnimator;
    private ValueAnimator mDelAnimator;

    public SpeedDial(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SpeedDial(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SpeedDial(Context context) {
        super(context);
    }

    public void setup(Launcher launcher, DragController controller) {
        mLauncher = launcher;
        mDragController = controller;
        mSearchBar = mLauncher.getDragLayer().findViewById(R.id.search_bar);

        LauncherAppState app = LauncherAppState.getInstance();
        mIconCache = app.getIconCache();

        // setup edit bottom view.
        mEditBottomView = mLauncher.getDragLayer().findViewById(R.id.edit_bottom_view);
        int[] ids = {
                R.id.edit_bottom_change_icon,
                R.id.edit_bottom_change_app,
                R.id.edit_bottom_delete
        };
        int[] tags = {
                EDIT_VIEW_ICON,
                EDIT_VIEW_CHANGE,
                EDIT_VIEW_DELETE
        };
        View v;
        for (int i = 0; i < ids.length; i++) {
            v = mLauncher.getDragLayer().findViewById(ids[i]);
            v.setOnClickListener(mLauncher);
            v.setTag(tags[i]);
        }

        mDecelerate = AnimationUtils.loadInterpolator(mLauncher,
                android.R.anim.decelerate_interpolator);

        mAccelerate = AnimationUtils.loadInterpolator(mLauncher,
                android.R.anim.accelerate_interpolator);

        mAnimatorSearch = ValueAnimator.ofFloat(0, 1f);
        mAnimatorSearch.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float value = (Float) animation.getAnimatedValue();
                mSearchBar.setTranslationY(mSearchBar.getHeight() * (1 - value));
                if (mAnimatorValue == value) {
                    int v = mSearchBar.getVisibility() == View.VISIBLE ? View.INVISIBLE
                            : View.VISIBLE;
                    mSearchBar.setVisibility(v);

                    v = mEditBottomView.getVisibility() == View.VISIBLE ? View.INVISIBLE
                            : View.VISIBLE;
                    mEditBottomView.setVisibility(v);
                    mAnimatorValue = 2f;
                }
            }
        });

        mAnimatorEdit = ValueAnimator.ofFloat(0, 1f);
        mAnimatorEdit.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float value = (Float) animation.getAnimatedValue();
                mEditBottomView.setTranslationY(mEditBottomView.getHeight() * (1 - value));
                if (mAnimatorValue == value) {
                    int v = mSearchBar.getVisibility() == View.VISIBLE ? View.INVISIBLE
                            : View.VISIBLE;
                    mSearchBar.setVisibility(v);

                    v = mEditBottomView.getVisibility() == View.VISIBLE ? View.INVISIBLE
                            : View.VISIBLE;
                    mEditBottomView.setVisibility(v);
                    mAnimatorValue = 2f;
                }
            }
        });

        mAlphaView = null;
        mAlphaAnimator = ValueAnimator.ofFloat(0, 0.7f);
        mAlphaAnimator.setDuration(ALPHA_DURATION);
        mAlphaAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float alpha = (Float) animation.getAnimatedValue();
                if (mAlphaView != null) {
                    mAlphaView.setAlpha(1f - alpha);
                } else {
                    int count = mBubbleViews.size() - 1;
                    for (int i = count; i >= 0; i--) {
                        BubbleView view = mBubbleViews.get(i);
                        if (view != mSelect)
                            view.setAlpha(1f - alpha);
                    }
                }
            }
        });

        mDelAnimator = ValueAnimator.ofFloat(1f, 0);
        mDelAnimator.setDuration(OUT_DURATION);
        mDelAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float alpha = (Float) animation.getAnimatedValue();
                mSelect.setAlpha(alpha);
            }
        });
        mDelAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeBubbleView();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationStart(Animator animation) {

            }

        });
    }

    public void startBind() {
        int count = mBubbleViews.size() - 1;
        for (int i = count; i >= 0; i--) {
            BubbleView view = mBubbleViews.get(i);
            removeBubbleView(view);
        }
    }

    public void finishBind() {
        update();
    }

    public void updateFromBind(ArrayList<AppInfo> appInfos) {
        HashSet<ComponentName> cns = new HashSet<ComponentName>();
        for (AppInfo info : appInfos) {
            cns.add(info.componentName);
        }

        int count = mBubbleViews.size() - 1;
        for (int i = count; i >= 0; i--) {
            BubbleView view = mBubbleViews.get(i);
            ShortcutInfo info = (ShortcutInfo) view.getTag();
            if (info.intent != null && cns.contains(info.intent.getComponent())) {
                info.mIcon = mIconCache.getIcon(info.intent);
                view.changeBitmap(info.mIcon);
            }
        }
    }

    public void removeBubbleViewFromBind(ArrayList<AppInfo> appInfos) {
        HashSet<ComponentName> cns = new HashSet<ComponentName>();
        for (AppInfo info : appInfos) {
            cns.add(info.componentName);
        }

        int count = mBubbleViews.size() - 1;
        for (int i = count; i >= 0; i--) {
            BubbleView view = mBubbleViews.get(i);
            ShortcutInfo info = (ShortcutInfo) view.getTag();
            if (info.intent != null && cns.contains(info.intent.getComponent())) {
                removeBubbleView(view);
                LauncherModel.deleteItemFromDatabase(mLauncher, info);
            }
        }
        update();
    }

    private void addBubbleView(ShortcutInfo info, Bitmap b) {
        BubbleView v = new BubbleView(mLauncher, b);
        v.setTag(info);
        v.setSize(mIconSize, mPadding);
        addView(v);
        mBubbleViews.add(v);
        v.setOnClickListener(mLauncher);
        v.setOnLongClickListener(mLauncher);

        v.setOnTouchListener(this);

        mDragController.addDropWorkSpace(v);
    }

    public void addBubbleViewFromBind(ShortcutInfo info) {
        Bitmap b = info.mIcon;
        if (b == null) {
            b = getDefaultIcon();
            info.mRecycle = false;
        }

        addBubbleView(info, b);

        int p = mBubbleViews.size() - 1;
        if (info.position != p) {
            LauncherModel.modifyItemInDatabase(mLauncher, info, p);
        }
    }

    public void addBubbleView(ShortcutInfo info) {
        Bitmap b = info.mIcon;
        int position = mBubbleViews.size();

        addBubbleView(info, b);
        mSelect = mBubbleViews.get(position);

        LauncherModel.addItemToDatabase(mLauncher, info);
    }

    private Bitmap getDefaultIcon() {
        if (mDefaultIcon == null) {
            mDefaultIcon = BitmapUtils.getIcon(Resources.getSystem(),
                    android.R.mipmap.sym_def_app_icon, mIconSize);
        }
        return mDefaultIcon;
    }

    public void changeIcon(int iconId) {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();

        if (i.mIconId == iconId) { // same icon
            return;
        }
        if (i.mRecycle) {
            mSelect.clearBitmap();
        }

        Bitmap b = BitmapUtils.getIcon(mLauncher.getResources(), iconId, mIconSize);
        i.mRecycle = true;
        i.mIconId = iconId;
        i.mIcon = null;
        if (b == null) {
            b = getDefaultIcon();
            i.mRecycle = false;
            i.mIconId = -1;
        }

        mSelect.changeBitmap(b);

        LauncherModel.updateItemInDatabase(mLauncher, i);
    }

    public void changeBubbleView(ShortcutInfo info) {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();
        if (i.mRecycle) {
            mSelect.clearBitmap();
            i.mRecycle = false;
        }
        i.intent = info.intent;
        i.mIcon = info.mIcon;
        i.mIconId = -1;

        mSelect.changeBitmap(info.mIcon);

        LauncherModel.updateItemInDatabase(mLauncher, i);
    }

    public void removeBubbleView(BubbleView view) {
        ShortcutInfo i = (ShortcutInfo) view.getTag();
        mBubbleViews.remove(view);
        removeView(view);
        mDragController.removeDropWorkSpace(view);

        if (i.mRecycle) {
            view.clearBitmap();
            i.mRecycle = false;
        }
    }

    public void removeBubbleView() {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();

        removeBubbleView(mSelect);
        LauncherModel.deleteItemFromDatabase(mLauncher, i);

        int s = mBubbleViews.size();

        if (s <= 0) {
            stopDrag();
            return;
        }
        update();
        mSelect = mBubbleViews.get(s - 1);
        mAlphaView = mSelect;
        mAlphaAnimator.setFloatValues(0.7f, 0);
        mAlphaAnimator.start();
    }

    public void deleteBubbleView() {
        mDelAnimator.start();
    }

    private void moveBubbleView(int position, float x, float y) {
        BubbleView v = mBubbleViews.get(position);
        if (v != null) {
            v.move(x, y);
        }
    }

    void initSize() {
        mMidPoint[0] = 0;
        mMidPoint[1] = 0;
        mPadding = mSize / 10;
        mIconSize = (mSize - mPadding * 2) / 3;
        mMidPoint[1] += (mIconSize + mPadding);
        if (mIconSize > IconConfig.iconSizeMax) {
            int offset = mIconSize - IconConfig.iconSizeMax;
            mPadding += offset;
            mMidPoint[0] += offset / 2;
            mMidPoint[1] += offset / 2;
            mIconSize = IconConfig.iconSizeMax;
        } else if (mIconSize < IconConfig.iconSizeMin) {
            mIconSize = IconConfig.iconSizeMin;
        }

        // do not forget to change size of bubble views existed.
        for (BubbleView v : mBubbleViews) {
            v.setSize(mIconSize, mPadding);
        }

        update();
    }

    public void update() {
        int count = mBubbleViews.size();

        float startX = mMidPoint[0];
        float startY = mMidPoint[1];

        if (count == 0) {
            return;
        }
        if (count > 6) {
            startY += (mIconSize + mPadding);
        }
        float iconX, iconY;
        int rNum = count % 3;

        if (rNum == 1) {
            iconX = startX + mIconSize + mPadding;
            iconY = startY - (mIconSize + mPadding) * (count / 3);
            moveBubbleView(count - 1, iconX, iconY);
        } else if (rNum == 2) {
            iconX = startX + (mIconSize + mPadding) / 2;
            iconY = startY - (mIconSize + mPadding) * (count / 3);
            moveBubbleView(count - 2, iconX, iconY);
            moveBubbleView(count - 1, iconX + mIconSize + mPadding, iconY);
        }

        rNum = count - rNum;
        iconX = startX;
        iconY = startY;
        for (int i = 0; i < rNum; i++) {
            moveBubbleView(i, iconX, iconY);
            iconX += (mIconSize + mPadding);
            if ((i + 1) % 3 == 0) {
                iconX = startX;
                iconY -= (mIconSize + mPadding);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // catch the size from onMeasure.
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthSize != heightSize) {
            try {
                throw new Exception("width != height");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mSize != widthSize) {
            mSize = widthSize;
            initSize();
        }

        // set measured dimension of children to mIconSize for touch event.
        int sizeMeasureSpec = MeasureSpec.makeMeasureSpec(mIconSize, MeasureSpec.EXACTLY);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.measure(sizeMeasureSpec, sizeMeasureSpec);
        }

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        if (mState == SPEED_DIAL_STATE_NORMAL) {
            return false;
        }

        if (action == MotionEvent.ACTION_DOWN && v instanceof BubbleView) {
            BubbleView bv = (BubbleView) v;
            mAlphaView = mSelect;
            startDrag(bv);
            return true;
        }
        return false;
    }

    private void showEditViews() {
        mAnimatorSearch.setFloatValues(1f, 0);
        mAnimatorSearch.setDuration(OUT_DURATION);
        mAnimatorSearch.setInterpolator(mAccelerate);
        mAnimatorValue = 0;
        mAnimatorEdit.setFloatValues(0, 1f);
        mAnimatorEdit.setDuration(IN_DURATION);
        mAnimatorEdit.setInterpolator(mDecelerate);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(mAnimatorSearch).before(mAnimatorEdit);
        animatorSet.start();
    }

    private void hideEditViews() {
        mAnimatorEdit.setFloatValues(1f, 0);
        mAnimatorEdit.setDuration(OUT_DURATION);
        mAnimatorEdit.setInterpolator(mAccelerate);
        mAnimatorValue = 0;
        mAnimatorSearch.setFloatValues(0, 1f);
        mAnimatorSearch.setDuration(IN_DURATION);
        mAnimatorSearch.setInterpolator(mDecelerate);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(mAnimatorEdit).before(mAnimatorSearch);
        animatorSet.start();
    }

    public void startDrag(BubbleView view) {
        if (mState != SPEED_DIAL_STATE_DRAG) {
            mState = SPEED_DIAL_STATE_DRAG;
            showEditViews();
            mLauncher.hideContextMenu();
        }
        mSelect = view;
        mSelectX = mSelect.getTranslationX();
        mSelectY = mSelect.getTranslationY();
        removeView(view);
        mDragController.removeDropWorkSpace(view);
        mDragController.startDrag(this, view);

        if (mAlphaView == view) {
            return;
        }
        mSelect.setAlpha(1f);
        mAlphaAnimator.setFloatValues(0, 0.7f);
        mAlphaAnimator.start();
    }

    public void stopDrag() {
        if (mState != SPEED_DIAL_STATE_NORMAL) {
            mState = SPEED_DIAL_STATE_NORMAL;
            hideEditViews();
            mAlphaView = null;
            mAlphaAnimator.setFloatValues(0.7f, 0);
            mAlphaAnimator.start();

            mLauncher.showContextMenu();
        }
    }

    public float getSelectX() {
        return mSelectX;
    }

    public float getSelectY() {
        return mSelectY;
    }

    public boolean isDragState() {
        if (mState == SPEED_DIAL_STATE_DRAG) {
            return true;
        }
        return false;
    }

    public boolean isFull() {
        return mBubbleViews.size() >= BUBBLE_VIEW_CAPACITY;
    }

    @Override
    public void onDropCompleted(View targetView) {
        if (targetView instanceof BubbleView) {
            BubbleView target = (BubbleView) targetView;
            ShortcutInfo si = (ShortcutInfo) mSelect.getTag();
            ShortcutInfo ti = (ShortcutInfo) target.getTag();
            int tIndex = mBubbleViews.indexOf(target);
            int sIndex = mBubbleViews.indexOf(mSelect);
            int sp = si.position;
            int tp = ti.position;
            LauncherModel.modifyItemInDatabase(mLauncher, si, tp);
            mBubbleViews.set(tIndex, mSelect);
            mBubbleViews.set(sIndex, target);
            LauncherModel.modifyItemInDatabase(mLauncher, ti, sp);
        }
        mLauncher.getDragLayer().removeView(mSelect);
        mDragController.addDropWorkSpace(mSelect);
        addView(mSelect);
        update();
    }
}
