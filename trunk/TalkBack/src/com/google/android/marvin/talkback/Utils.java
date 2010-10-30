/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.marvin.talkback;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CompoundButton;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class contains utility methods.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class Utils {

    /** Tag for logging. */
    private static final String LOG_TAG = Utils.class.getSimpleName();

    /** Invalid version code for a package. */
    public static final int INVALID_VERSION_CODE = -1;

    /** Cached set of the system features. */
    private static final Set<String> sSystemFeatureSet = new HashSet<String>();
    
    /** String constant. */
    private static final String SPACE = " ";
    
    // this is need as a workaround for the adding of CompoundButton state by
    // the framework
    private static String sValueChecked;

    private static String sValueNotChecked;
    

    /**
     * @return The name of the current {@link android.app.Activity} given
     *         through the current <code>context</code>.
     */
    public static String getCurrentActivityName(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Service.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            return tasks.get(0).topActivity.getClassName();
        }
        return null;
    }

    /**
     * @return If the system has a feature with the given <code>featureName</code>
     *         and false if the feature is not present or the platform version is
     *         less than 5 in which the features APIs appeared.
     */
    public static boolean hasSystemFeature(Context context, String featureName) {
        if (Build.VERSION.SDK_INT <= 4) {
            return false;
        }
        if (sSystemFeatureSet.isEmpty()) {
            PackageManager packageManager = context.getPackageManager();
            Method getSystemAvailableFeatures = null;
            try {
                getSystemAvailableFeatures = packageManager.getClass().getMethod(
                        "getSystemAvailableFeatures", (Class[]) null);
            } catch (NoSuchMethodException nsme) {
                return false;
            }
            try {
                Object[] features = (Object[]) getSystemAvailableFeatures.invoke(packageManager,
                        (Object[]) null);
                for (Object feature : features) {
                    Field field = feature.getClass().getField("name");
                    String name = (String) field.get(feature);
                    sSystemFeatureSet.add(name);
                }
            } catch (InvocationTargetException ite) {
                return false;
            } catch (IllegalAccessException iae) {
                return false;
            } catch (NoSuchFieldException nsfe) {
                return false;
            }
        }
        return sSystemFeatureSet.contains(featureName);
    }

    /**
     * @return The package version code or {@link #INVALID_VERSION_CODE}
     *         if the package does not exist.
     */
    public static int getVersionCode(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Could not find package: " + packageName, e);
            return INVALID_VERSION_CODE;
        }
    }

    /**
     * @return The package version name or <code>null</code> if the package
     *         does not exist.
     */
    public static String getVersionName(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Could not find package: " + packageName, e);
            return null;
        }
    }
    
    /**
     * This is a workaround for the addition of checked/not checked message
     * generated by the framework which should actually happen the the
     * accessibility service.
     */
    private static void ensureCompoundButtonWorkaround() {
        if (sValueChecked == null || sValueNotChecked == null) {
            Context context = TalkBackService.asContext();
            sValueChecked = context.getString(R.string.value_checked);
            sValueNotChecked = context.getString(R.string.value_not_checked);
        }
    }
    
    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     * 
     * @param context The context from which to load required resources.
     * @param event The event.
     * @return The event text.
     */
    public static StringBuilder getEventText(Context context, AccessibilityEvent event) {
        ensureCompoundButtonWorkaround();

        StringBuilder aggregator = new StringBuilder();
        List<CharSequence> eventText = event.getText();
        if (context == null) {
            String s = "";
        }
        Class<?> eventClass = ClassLoadingManager.getInstance().loadOrGetCachedClass(context,
                event.getClassName().toString(), event.getPackageName().toString());

        // here we have a special case since the framework is adding
        // the string for the state of a CompoundButton but we also get the
        // isChecked attribute
        int stateStringIndex = -1;
        if (eventClass != null && CompoundButton.class.isAssignableFrom(eventClass)) {
            for (int i = 0, count = eventText.size(); i < count; i++) {
                CharSequence next = eventText.get(i);
                if (sValueChecked.equals(next) || sValueNotChecked.equals(next)) {
                    stateStringIndex = i;
                    break;
                }
            }
        }

        for (int i = 0, count = eventText.size(); i < count; i++) {
            if (i != stateStringIndex) {
                aggregator.append(eventText.get(i));
                aggregator.append(SPACE);
            }
        }

        if (aggregator.length() > 0) {
            aggregator.deleteCharAt(aggregator.length() - 1);
        }

        return aggregator;
    }
}
