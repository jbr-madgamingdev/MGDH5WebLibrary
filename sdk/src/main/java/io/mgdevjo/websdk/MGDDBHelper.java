/*
 *
 *  * MGD is pleased to support the open source community by making MGDWebSDK available.
 *  *
 *  * Copyright (C) 2024 MAD Gaming Development, a Vertex-Digital company. All rights reserved.
 *  * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *  *
 *  * https://opensource.org/licenses/BSD-3-Clause
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 *  *
 *  *
 *
 */

package io.mgdevjo.websdk;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MGDDBHelper interacts with the database, such as managing database creation and
 * the version management.
 */
public class MGDDBHelper extends SQLiteOpenHelper {

    /**
     * log filter
     */
    private static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDDBHelper";

    /**
     * name of the database file
     */
    private static final String MGD_DATABASE_NAME = "MGD.db";

    /**
     * the first version code of database
     */
    private static final int MGD_DATABASE_FIRST_VERSION = 1;

    /**
     * current version code of the database (starting at <code>MGD_DATABASE_FIRST_VERSION</code>)
     */
    private static final int MGD_DATABASE_VERSION = 2;

    private static MGDDBHelper sInstance = null;

    private static AtomicBoolean isDBUpgrading = new AtomicBoolean(false);

    private MGDDBHelper(Context context) {
        super(context, MGD_DATABASE_NAME, null, MGD_DATABASE_VERSION);
    }

    static synchronized MGDDBHelper createInstance(Context context) {
        if (null == sInstance) {
            sInstance = new MGDDBHelper(context);
        }
        return sInstance;
    }

    public static synchronized MGDDBHelper getInstance() {
        if (null == sInstance) {
            throw new IllegalStateException("MGDDBHelper::createInstance() needs to be called before MGDDBHelper::getInstance()!");
        }
        return sInstance;
    }

    /**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     *
     * @param db The database.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // create sessionData table
        db.execSQL(MGDDataHelper.CREATE_TABLE_SQL);

        // upgrade SP if need(session data save in SP on sdk 1.0)
        onUpgrade(db, -1, MGD_DATABASE_VERSION);

        doUpgrade(db, MGD_DATABASE_FIRST_VERSION, MGD_DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (isDBUpgrading.compareAndSet(false, true)) {
            long startTime = System.currentTimeMillis();
            MGDUtils.log(TAG, Log.INFO, "onUpgrade start, from " + oldVersion + " to " + newVersion + ".");
            if (-1 == oldVersion) {
                MGDEngine.getInstance().getRuntime().postTaskToThread(new Runnable() {
                    @Override
                    public void run() {
                        MGDUtils.removeAllSessionCache();
                        isDBUpgrading.set(false);
                    }
                }, 0L);
            } else {
                doUpgrade(db, oldVersion, newVersion);
                isDBUpgrading.set(false);
            }
            MGDUtils.log(TAG, Log.INFO, "onUpgrade finish, cost " + (System.currentTimeMillis() - startTime) + "ms.");
        }
    }

    /**
     * Called when the database needs to be upgraded.
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    private void doUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1: //2.0 version
                upgradeToVersion_2(db);
                break;
            default:
                break;
        }
    }

    /**
     * upgrade database from version 1 to version 2.
     *
     * @param db The database.
     */
    private void upgradeToVersion_2(SQLiteDatabase db) {
        // create resourceData table
        db.execSQL(MGDResourceDataHelper.CREATE_TABLE_SQL);
    }

    /**
     * Indicates whether is upgrading or not. If return true, It will fail to create session.
     * @return is Upgrading or not
     */
    public boolean isUpgrading() {
        return isDBUpgrading.get();
    }

}
