package net.darkion.keyboardanimationfix;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.Dialog;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;


/**
 * This module animates the keyboard view using ValueAnimators instated
 * of the old, ugly XML animations which stutter and look hideous.
 * The advantage of using ValueAnimator is that panning insets will
 * be measured correctly; however, this will cause lag depending on
 * the view hierarchy complexity.
 * <p>
 * Technical details:
 * <p>
 * ViewRootImpl#performTraversals is called when keyboard location
 * changes on screen. Due to the complex nature of this method, old
 * phones will lag when keyboard slides in/out.
 * <p>
 * InputMethodService#mInsetsComputer is responsible for the panning
 * effect seen in some apps such as WhatsApp. It is a callback for
 * ViewTreeObserver.
 * <p>
 * Google is yet to fix the decade-old implementation of keyboard
 * and rotation animations.
 *
 * @author Darkion Avey (http://darkion.net)
 */
public class Main implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Override
    public void initZygote(StartupParam startupParam) {
    }

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        final Class<?> cSoftInputWindow = XposedHelpers.findClassIfExists(" android.inputmethodservice.SoftInputWindow", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(Dialog.class, "hide", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if (cSoftInputWindow.isInstance(param.thisObject)) {
                    final View view = ((Dialog) param.thisObject).getWindow().getDecorView();
                    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.Y, 0f, view.getHeight());
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            view.setVisibility(View.GONE);
                            resetView(view, true);
                        }
                    });
                    animator.setInterpolator(LogAccelerateInterpolator.getInstance());
                    animator.setDuration(ANIMATION_DURATION);
                    animator.start();
                    param.setResult(null);
                }
            }

        });


        XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (cSoftInputWindow.isInstance(param.thisObject)) {
                    runEntranceAnimation((Dialog) param.thisObject);
                }
            }
        });

        //called when keyboard view is ready to be shown;
        //this code overrides default animations
        XposedHelpers.findAndHookMethod(InputMethodService.class,
                "showWindowInner", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        Window window = (Window) XposedHelpers.callMethod(XposedHelpers.getObjectField(param.thisObject, "mWindow"), "getWindow");
                        window.setWindowAnimations(0);
                    }
                });

    }

    private static void runEntranceAnimation(final Dialog dialog) {
        final Window window = (Window) XposedHelpers.callMethod(dialog, "getWindow");
        final View view = dialog.getWindow().getDecorView();
        final int height = view.getHeight();
        final WindowManager.LayoutParams lp = window.getAttributes();

        resetView(view, true);
        view.setTranslationY(height);
        view.post(new Runnable() {
            @Override
            public void run() {
                lp.height = height;
                window.setAttributes(lp);
                ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.Y, view.getHeight(), 0.0f);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        resetView(view, false);
                    }
                });
                animator.setInterpolator(LogDecelerateInterpolator.getInstance());
                animator.setDuration(ANIMATION_DURATION);
                animator.start();
            }
        });
    }

    private static void resetView(View view, boolean off) {
        if (view == null) return;
        float value = off ? 0f : 1f;
        view.setAlpha(value);
        view.setScaleY(value);
        view.setScaleX(value);
        if (!off) view.setVisibility(View.VISIBLE);
    }

    private static final int INTERPOLATOR_DRIFT = 50;
    private static final long ANIMATION_DURATION = 280;

    /**
     * @author Launcher3
     */
    private static class LogAccelerateInterpolator implements TimeInterpolator {
        private static final LogAccelerateInterpolator LOG_ACCELERATE_INTERPOLATOR = new LogAccelerateInterpolator(INTERPOLATOR_DRIFT, 0);

        public static LogAccelerateInterpolator getInstance() {
            return LOG_ACCELERATE_INTERPOLATOR;
        }

        int mBase;
        int mDrift;
        final float mLogScale;

        private LogAccelerateInterpolator(int base, int drift) {
            mBase = base;
            mDrift = drift;
            mLogScale = 1f / computeLog(1, mBase, mDrift);
        }

        static float computeLog(float t, int base, int drift) {
            return (float) -Math.pow(base, -t) + 1 + (drift * t);
        }

        @Override
        public float getInterpolation(float t) {
            return Float.compare(t, 1f) == 0 ? 1f : 1 - computeLog(1 - t, mBase, mDrift) * mLogScale;
        }
    }

    /**
     * @author Launcher3
     */
    private static class LogDecelerateInterpolator implements TimeInterpolator {
        private final static LogDecelerateInterpolator LOG_DECELERATE_INTERPOLATOR = new LogDecelerateInterpolator(INTERPOLATOR_DRIFT, 0);

        public static LogDecelerateInterpolator getInstance() {
            return LOG_DECELERATE_INTERPOLATOR;
        }

        private int mBase;
        private int mDrift;
        private final float mLogScale;

        private LogDecelerateInterpolator(int base, int drift) {
            mBase = base;
            mDrift = drift;
            mLogScale = 1f / computeLog(1, mBase, mDrift);
        }

        private static float computeLog(float t, int base, int drift) {
            return (float) -Math.pow(base, -t) + 1 + (drift * t);
        }

        @Override
        public float getInterpolation(float t) {
            return computeLog(t, mBase, mDrift) * mLogScale;
        }
    }

}
