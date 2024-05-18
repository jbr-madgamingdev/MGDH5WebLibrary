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

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * Interact with the overall file operations.
 */
public class MGDFileUtils {

    /**
     * Log filter
     */
    private static final String TAG = MGDConstants.MGDWEB_SDK_LOG_PREFIX + "MGDFileUtils";

    /**
     *  Template  extensions
     */
    private static final String TEMPLATE_EXT = ".tpl";

    /**
     * Data extensions
     */
    private static final String DATA_EXT = ".data";

    /**
     * Html extensions
     */
    private static final String HTML_EXT = ".html";

    /**
     * Response header extensions.
     */
    private static final String HEADER_EXT = ".header";

    /**
     * The max percent threshold of cache.
     * If the size of cache exceed max threshold, it will trim cache to{@link MGDFileUtils#THRESHOLD_OF_CACHE_MIN_PERCENT}
     */
    private static final double THRESHOLD_OF_CACHE_MAX_PERCENT = 0.8;

    /**
     * The min percent threshold of cache.
     */
    private static final double THRESHOLD_OF_CACHE_MIN_PERCENT = 0.25;

    /**
     *
     * @return Returns the absolute path to the specific cache directory on
     *  the filesystem (including File.separator at the end of path).
     */
    static String getMGDCacheDirPath() {
        String dirPath = MGDEngine.getInstance().getRuntime().getMGDCacheDir().getAbsolutePath();
        if (!dirPath.endsWith(File.separator)) {
            dirPath += File.separator;
        }
        return dirPath;
    }

    /**
     *
     * @return Returns the absolute path to the resource cache directory on
     *  the filesystem (including File.separator at the end of path).
     */
    static String getMGDResourceCachePath() {
        String dirPath = MGDEngine.getInstance().getRuntime().getMGDResourceCacheDir().getAbsolutePath();
        if (!dirPath.endsWith(File.separator)) {
            dirPath += File.separator;
        }
        return dirPath;
    }

    /**
     *
     * @param sessionId session id
     * @return The path of the directory holding MGD template cache files.
     */
    static String getMGDTemplatePath(String sessionId) {
        return getMGDCacheDirPath() + sessionId + TEMPLATE_EXT;
    }

    /**
     *
     * @param sessionId session id
     * @return The path of the directory holding MGD data cache files.
     */
    static String getMGDDataPath(String sessionId) {
        return getMGDCacheDirPath() + sessionId + DATA_EXT;
    }

    /**
     *
     * @param sessionId session id
     * @return he path of the directory holding MGD response http header cache files.
     */
    static String getMGDHeaderPath(String sessionId) {
        return getMGDCacheDirPath() + sessionId + HEADER_EXT;
    }

    /**
     *
     * @param sessionId session id
     * @return The path of the directory holding MGD html cache files.
     */
    static String getMGDHtmlPath(String sessionId) {
        return getMGDCacheDirPath() + sessionId + HTML_EXT;
    }

    /**
     *
     * @param resourceName resource file name
     * @return The path of the resource file.
     */
    public static String getMGDResourcePath(String resourceName) {
        return getMGDResourceCachePath() + resourceName;
    }

    /**
     *
     * @param resourceName resource file name
     * @return The path of the resource header file.
     */
    public static String getMGDResourceHeaderPath(String resourceName) {
        return getMGDResourceCachePath() + resourceName + HEADER_EXT;
    }

    /**
     *
     * @param sessionId session id
     * @return Return {@code true} if all of the cache files have been deleted, such as html template and the data cache files.
     */
    static boolean deleteMGDFiles(String sessionId) {
        boolean deleteSuccess = true;
        File htmlFile = new File(getMGDHtmlPath(sessionId));
        if (htmlFile.exists()) {
            deleteSuccess = htmlFile.delete();
        }

        File templateFile = new File(getMGDTemplatePath(sessionId));
        if (templateFile.exists()) {
            deleteSuccess &= templateFile.delete();
        }

        File dataFile = new File(getMGDDataPath(sessionId));
        if (dataFile.exists()) {
            deleteSuccess &= dataFile.delete();
        }

        File headerFile = new File(getMGDHeaderPath(sessionId));
        if (headerFile.exists()){
            deleteSuccess &= headerFile.delete();
        }

        return deleteSuccess;
    }

    /**
     *
     * @param resourceId resource file name
     * @return Return {@code true} if all of the cache files have been deleted, such as resource file and resource header file.
     */
    static boolean deleteResourceFiles(String resourceId) {
        boolean deleteSuccess = true;
        File resourceFile = new File(getMGDResourcePath(resourceId));
        if (resourceFile.exists()) {
            deleteSuccess = resourceFile.delete();
        }

        File headerFile = new File(getMGDHeaderPath(resourceId));
        if (headerFile.exists()){
            deleteSuccess &= headerFile.delete();
        }

        return deleteSuccess;
    }

    /**
     * This method computes hash value by using specified SHA1 digest algorithm and compares hash value to the specified hash @{code targetSha1}.
     *
     * @param content    Data
     * @param targetSha1 The specified hash value
     * @return {@code true} if the given hash value
     *          equivalent to computed hash value, {@code false} otherwise
     */
    static boolean verifyData(String content, String targetSha1) {
        return !TextUtils.isEmpty(content) && !TextUtils.isEmpty(targetSha1) &&
                targetSha1.equals(MGDUtils.getSHA1(content));
    }

    /**
     * This method computes hash value by using specified SHA1 digest algorithm and compares hash value to the specified hash @{code targetSha1}.
     *
     * @param content   Data bytes
     * @param targetSha1 The specified hash value
     * @return {@code true} if the given hash value
     *          equivalent to computed hash value, {@code false} otherwise
     */
    public static boolean verifyData(byte[] content, String targetSha1) {
        return content != null && !TextUtils.isEmpty(targetSha1) &&
                targetSha1.equals(MGDUtils.getSHA1(content));
    }

    /**
     *
     * @param file The file path of template
     * @return Returns a string containing all of the content read from template file.
     */
    static String readFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return null;
        }

        // read
        BufferedInputStream bis = null;
        InputStreamReader reader = null;
        char[] buffer;
        String rtn = null;
        int n;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            reader = new InputStreamReader(bis);
            int size = (int) file.length();
            if (size > 1024 * 12) {
                buffer = new char[1024 * 4];
                StringBuilder result = new StringBuilder(1024 * 12);
                while (-1 != (n = reader.read(buffer))) {
                    result.append(buffer, 0, n);
                }
                rtn = result.toString();
            } else {
                buffer = new char[size];
                n = reader.read(buffer);
                rtn = new String(buffer, 0, n);
            }
        } catch (Throwable e) {
            MGDUtils.log(TAG, Log.ERROR, "readFile error:(" + file.getName() + ") " + e.getMessage());
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (Exception e) {
                    MGDUtils.log(TAG, Log.ERROR, "readFile close error:(" + file.getName() + ") " + e.getMessage());
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    MGDUtils.log(TAG, Log.ERROR, "readFile close error:(" + file.getName() + ") " + e.getMessage());
                }
            }
        }
        return rtn;
    }

    /**
     *
     * @param file path of the file to read
     * @return Returns the content bytes read from the file.
     */
    public static byte[] readFileToBytes(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return null;
        }

        // read
        BufferedInputStream bis = null;
        ByteArrayOutputStream out = null;
        byte[] rtn = null;
        int n;
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            int size = (int) file.length();
            if (size > 1024 * 12) {
                out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024 * 4];
                while ((n = bis.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                rtn = out.toByteArray();
            } else {
                rtn = new byte[size];
                n = bis.read(rtn);
            }
        } catch (Throwable e) {
            MGDUtils.log(TAG, Log.ERROR, "readFile error:(" + file.getName() + ") " + e.getMessage());
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (Exception e) {
                    MGDUtils.log(TAG, Log.ERROR, "readFile close error:(" + file.getName() + ") " + e.getMessage());
                }
            }
        }
        return rtn;
    }

    /**
     * Write string to the file represented by
     * the specified <code>File</code> object.
     *
     * @param str      The string is to be saved
     * @param filePath path to write
     * @return Returns {@code true} if string is saved successfully.
     */
    static boolean writeFile(String str, String filePath) {
        return writeFile(str.getBytes(), filePath);
    }

    /**
     * Write bytes to the specific file.
     *
     * @param content   The data is to be saved
     * @param filePath  path to write
     * @return Returns {@code true} if string is saved successfully.
     */
    static boolean writeFile(byte[] content, String filePath) {
        File file = new File(filePath);
        FileOutputStream fos = null;
        try {
            if (!file.exists() && !file.createNewFile()) {
                return false;
            }
            fos = new FileOutputStream(file);
            fos.write(content);
            fos.flush();
            return true;
        } catch (Throwable e) {
            MGDUtils.log(TAG, Log.ERROR, "writeFile error:(" + filePath + ") " + e.getMessage());
        } finally {
            if (null != fos) {
                try {
                    fos.close();
                } catch (Throwable e) {
                    MGDUtils.log(TAG, Log.ERROR, "writeFile close error:(" + filePath + ") " + e.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Deletes all of the files or directory denoted by this file path
     *
     * @param file The file to be deleted
     */
    static boolean deleteAllChildFiles(File file) {
        boolean deleteSuccess = true;
        if (null != file && file.exists()) {
            if (file.isFile()) {
                deleteSuccess = file.delete();
            } else if (file.isDirectory()) {
                File[] childFiles = file.listFiles();
                if (null != childFiles) {
                    for (File childFile : childFiles) {
                        deleteSuccess &= deleteAllChildFiles(childFile);
                    }
                }
            }
        }
        return deleteSuccess;
    }

    /**
     * Check whether the MGD cache has been exceed the limit {@link MGDConfig#MGD_CACHE_MAX_SIZE}.
     * If the size of MGD cache exceeds, then it will remove the elder cache
     * until the size is less than threshold {@link MGDFileUtils#THRESHOLD_OF_CACHE_MIN_PERCENT}.
     */
    static void checkAndTrimCache() {
        HashMap<String, List<String>> currentCacheFileMap = new HashMap<String, List<String>>();
        long startTime = System.currentTimeMillis();
        long cacheFileSize = calcCacheSize(getMGDCacheDirPath(), currentCacheFileMap);

        final long MAX_CACHE_SIZE = MGDEngine.getInstance().getConfig().MGD_CACHE_MAX_SIZE;

        if (cacheFileSize > (MAX_CACHE_SIZE * THRESHOLD_OF_CACHE_MAX_PERCENT)) {
            MGDUtils.log(TAG, Log.INFO, "now try clear cache, current cache size: " + (cacheFileSize / 1024 / 1024) + "m");

            List<MGDDataHelper.SessionData> allSessions = MGDDataHelper.getAllSessionByHitCount();

            long fileSize ;
            MGDDataHelper.SessionData sessionData;
            for (int i = 0; i < allSessions.size(); i++) {
                sessionData = allSessions.get(i);
                List<String> files = currentCacheFileMap.get(sessionData.sessionId);

                if (files != null && files.size() > 0) {
                    for (String filePath : files) {
                        File file = new File(filePath);
                        if (file.isFile() && file.exists()) {
                            String fileName = file.getName();
                            fileSize = file.length();
                            if (file.delete()) {
                                cacheFileSize -= fileSize;
                                MGDDataHelper.removeSessionData(fileName);
                                MGDUtils.log(TAG, Log.INFO, "delete " + file.getAbsolutePath());
                            }
                        }
                    }
                }

                if (cacheFileSize <= MAX_CACHE_SIZE * THRESHOLD_OF_CACHE_MIN_PERCENT) {
                    break;
                }
            }

            MGDUtils.log(TAG, Log.INFO, "checkAndTrimCache: finish , cost " + (System.currentTimeMillis() - startTime) + "ms.");
        }
    }

    /**
     * Check whether the resource cache has been exceed the limit {@link MGDConfig#MGD_RESOURCE_CACHE_MAX_SIZE}.
     * If the size of MGD cache exceeds, then it will remove the elder cache
     * until the size is less than threshold {@link MGDFileUtils#THRESHOLD_OF_CACHE_MIN_PERCENT}.
     */
    static void checkAndTrimResourceCache() {
        HashMap<String, List<String>> currentCacheFileMap = new HashMap<String, List<String>>();
        long startTime = System.currentTimeMillis();
        long cacheFileSize = calcCacheSize(getMGDResourceCachePath(), currentCacheFileMap);

        final long MAX_CACHE_SIZE = MGDEngine.getInstance().getConfig().MGD_RESOURCE_CACHE_MAX_SIZE;

        if (cacheFileSize > (MAX_CACHE_SIZE * THRESHOLD_OF_CACHE_MAX_PERCENT)) {
            MGDUtils.log(TAG, Log.INFO, "now try clear cache, current cache size: " + (cacheFileSize / 1024 / 1024) + "m");

            List<MGDResourceDataHelper.ResourceData> allSessions = MGDResourceDataHelper.getAllResourceData();

            long fileSize ;
            MGDResourceDataHelper.ResourceData sessionData;
            for (int i = 0; i < allSessions.size(); i++) {
                sessionData = allSessions.get(i);
                List<String> files = currentCacheFileMap.get(sessionData.resourceId);

                if (files != null && files.size() > 0) {
                    for (String filePath : files) {
                        File file = new File(filePath);
                        if (file.isFile() && file.exists()) {
                            String fileName = file.getName();
                            fileSize = file.length();
                            if (file.delete()) {
                                cacheFileSize -= fileSize;
                                MGDResourceDataHelper.removeResourceData(fileName);
                                MGDUtils.log(TAG, Log.INFO, "delete " + file.getAbsolutePath());
                            }
                        }
                    }
                }

                if (cacheFileSize <= MAX_CACHE_SIZE * THRESHOLD_OF_CACHE_MIN_PERCENT) {
                    break;
                }
            }

            MGDUtils.log(TAG, Log.INFO, "checkAndTrimCache: finish , cost " + (System.currentTimeMillis() - startTime) + "ms.");
        }
    }

    private static long calcCacheSize(String cacheDirPath, Map<String, List<String>> currentCacheFileMap) {
        File cacheRootDir = new File(cacheDirPath);
        if (cacheRootDir.exists() && cacheRootDir.isDirectory()) {
            File[] childFiles = cacheRootDir.listFiles();
            if (childFiles != null && childFiles.length > 0) {
                long cacheFileSize = 0L;
                String fileName;
                File file ;
                List<String> files;
                for (File childFile : childFiles) {
                    file = childFile;
                    cacheFileSize += file.length();
                    fileName = file.getName();

                    files = currentCacheFileMap.get(fileName);
                    if (files == null) {
                        files = new ArrayList<String>();
                    }

                    files.add(file.getAbsolutePath());
                    currentCacheFileMap.put(fileName, files);
                }

                return cacheFileSize;
            }
        }
        return 0;
    }

    /**
     *
     * @param headers response headers
     * @return the string which represent the last response header split by "\r\n
     */
    static String convertHeadersToString(Map<String, List<String>> headers) {
        if (headers != null && headers.size() > 0) {
            StringBuilder headerString = new StringBuilder();
            Set<Map.Entry<String, List<String>>> entries =  headers.entrySet();
            for (Map.Entry<String, List<String>> entry : entries) {
                String key = entry.getKey();
                if (!TextUtils.isEmpty(key)) {
                    List<String> values = entry.getValue();
                    for (String value : values) {
                        if (!TextUtils.isEmpty(value)) {
                            headerString.append(key).append(" : ");
                            headerString.append(value).append("\r\n");
                        }
                    }
                }
            }
            return headerString.toString();
        }

        return "";
    }

    /**
     * Get headers from local cache file
     *
     * @param headerPath header file path
     * @return The last http response headers from local cache.
     */
    public static Map<String, List<String>> getHeaderFromLocalCache(String headerPath) {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        File headerFile = new File(headerPath);
        if (headerFile.exists()) {
            String headerString = readFile(headerFile);
            if (!TextUtils.isEmpty(headerString)) {
                String[] headerArray = headerString.split("\r\n");
                if (headerArray.length > 0) {
                    List<String> tmpHeaderList;
                    for (String header : headerArray) {
                        String[] keyValues = header.split(" : ");
                        if (keyValues.length == 2) {
                            String key = keyValues[0].trim();
                            tmpHeaderList = headers.get(key.toLowerCase());
                            if (null == tmpHeaderList) {
                                tmpHeaderList = new ArrayList<String>(1);
                                headers.put(key.toLowerCase(), tmpHeaderList);
                            }
                            tmpHeaderList.add(keyValues[1].trim());
                        }
                    }
                }
            }
        }

        return headers;
    }
}
